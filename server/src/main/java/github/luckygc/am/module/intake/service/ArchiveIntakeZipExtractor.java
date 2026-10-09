package github.luckygc.am.module.intake.service;

import static github.luckygc.am.module.intake.service.ArchiveIntakePackageParser.MAX_SINGLE_FILE_BYTES;
import static github.luckygc.am.module.intake.service.ArchiveIntakePackageParser.MAX_UNCOMPRESSED_BYTES;
import static github.luckygc.am.module.intake.service.ArchiveIntakePackageParser.MAX_ZIP_ENTRIES;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;

import github.luckygc.am.common.exception.BadRequestException;

/** ZIP 安全检查及随机临时文件流式解包。 */
final class ArchiveIntakeZipExtractor {

    private static final int ZIP_END_SIGNATURE = 0x06054b50;
    private static final int ZIP_CENTRAL_SIGNATURE = 0x02014b50;
    private static final int ZIP64_SENTINEL = 0xffff;
    private static final int COPY_BUFFER_SIZE = 8192;

    ExtractedPackage extract(Path packagePath, Path tempDirectory) {
        Map<String, ExtractedEntry> extracted = new LinkedHashMap<>();
        Set<String> normalizedNames = new HashSet<>();
        int zipEntryCount = 0;
        long totalBytes = 0;
        try (ZipFile zip = new ZipFile(packagePath.toFile(), StandardCharsets.UTF_8)) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                zipEntryCount++;
                if (zipEntryCount > MAX_ZIP_ENTRIES) {
                    throw invalid("信息包 ZIP 条目不能超过 1000 个");
                }
                validateCompression(entry);
                String name = validateEntryName(entry.getName(), entry.isDirectory());
                if (!normalizedNames.add(name)) {
                    throw invalid("信息包包含重复路径：" + name);
                }
                if (entry.isDirectory()) {
                    extracted.put(name, new ExtractedEntry(name, true, null, 0, null));
                    continue;
                }
                ExtractedFile file =
                        extractFile(zip.getInputStream(entry), name, tempDirectory, totalBytes);
                totalBytes += file.size();
                extracted.put(
                        name,
                        new ExtractedEntry(name, false, file.path(), file.size(), file.sha256()));
            }
        } catch (ZipException exception) {
            throw invalid("信息包不是合法 ZIP 文件，或使用了加密/不支持的压缩算法");
        } catch (IOException exception) {
            throw invalid("信息包读取失败");
        }
        if (extracted.isEmpty()) {
            throw invalid("信息包 ZIP 不能为空");
        }

        return new ExtractedPackage(
                Collections.unmodifiableMap(extracted), totalBytes, zipEntryCount);
    }

    void inspectHeaders(Path packagePath, long compressedBytes) {
        int tailSize = Math.toIntExact(Math.min(compressedBytes, 65557));
        byte[] tail = new byte[tailSize];
        try (FileChannel channel = FileChannel.open(packagePath, StandardOpenOption.READ)) {
            readFully(channel, ByteBuffer.wrap(tail), compressedBytes - tailSize);
            int endOffset = findEndRecord(tail);
            if (endOffset < 0 || endOffset + 22 > tail.length) {
                throw invalid("信息包不是合法 ZIP 文件");
            }
            inspectCentralDirectory(channel, tail, endOffset, compressedBytes);
        } catch (IOException exception) {
            throw invalid("信息包读取失败");
        }
    }

    private void inspectCentralDirectory(
            FileChannel channel, byte[] endBytes, int endOffset, long compressedBytes)
            throws IOException {
        ByteBuffer end =
                ByteBuffer.wrap(endBytes, endOffset, endBytes.length - endOffset)
                        .order(ByteOrder.LITTLE_ENDIAN);
        if (end.getInt() != ZIP_END_SIGNATURE) {
            throw invalid("信息包不是合法 ZIP 文件");
        }
        int diskNumber = Short.toUnsignedInt(end.getShort());
        int centralDisk = Short.toUnsignedInt(end.getShort());
        int diskEntryCount = Short.toUnsignedInt(end.getShort());
        int entryCount = Short.toUnsignedInt(end.getShort());
        long centralSize = Integer.toUnsignedLong(end.getInt());
        long centralOffset = Integer.toUnsignedLong(end.getInt());
        if (diskNumber != 0 || centralDisk != 0 || diskEntryCount != entryCount) {
            throw invalid("信息包不支持分卷 ZIP");
        }
        if (entryCount == ZIP64_SENTINEL
                || centralSize == 0xffffffffL
                || centralOffset == 0xffffffffL) {
            throw invalid("信息包不支持 ZIP64 格式");
        }
        if (entryCount > MAX_ZIP_ENTRIES) {
            throw invalid("信息包 ZIP 条目不能超过 1000 个");
        }
        long centralEnd = centralOffset + centralSize;
        long absoluteEndOffset = compressedBytes - endBytes.length + endOffset;
        if (centralOffset > compressedBytes
                || centralEnd > compressedBytes
                || centralEnd > absoluteEndOffset) {
            throw invalid("信息包 ZIP 目录结构非法");
        }

        long offset = centralOffset;
        for (int index = 0; index < entryCount; index++) {
            if (offset + 46 > centralEnd) {
                throw invalid("信息包 ZIP 目录结构非法");
            }
            ByteBuffer centralHeader = ByteBuffer.allocate(46).order(ByteOrder.LITTLE_ENDIAN);
            readFully(channel, centralHeader, offset);
            centralHeader.flip();
            if (centralHeader.getInt() != ZIP_CENTRAL_SIGNATURE) {
                throw invalid("信息包 ZIP 目录结构非法");
            }
            centralHeader.position(8);
            int flags = Short.toUnsignedInt(centralHeader.getShort());
            int method = Short.toUnsignedInt(centralHeader.getShort());
            if ((flags & 0x0001) != 0 || (flags & 0x0040) != 0) {
                throw invalid("信息包不允许加密 ZIP 条目");
            }
            if (method != ZipEntry.STORED && method != ZipEntry.DEFLATED) {
                throw invalid("信息包包含不支持的压缩算法");
            }
            centralHeader.position(28);
            int nameLength = Short.toUnsignedInt(centralHeader.getShort());
            int extraLength = Short.toUnsignedInt(centralHeader.getShort());
            int commentLength = Short.toUnsignedInt(centralHeader.getShort());
            long next = offset + 46 + nameLength + extraLength + commentLength;
            if (next > centralEnd) {
                throw invalid("信息包 ZIP 目录结构非法");
            }
            offset = next;
        }
    }

    private int findEndRecord(byte[] bytes) {
        int minimumOffset = Math.max(0, bytes.length - 65557);
        for (int offset = bytes.length - 22; offset >= minimumOffset; offset--) {
            if (littleEndianInt(bytes, offset) == ZIP_END_SIGNATURE
                    && offset + 22 + littleEndianShort(bytes, offset + 20) == bytes.length) {
                return offset;
            }
        }
        return -1;
    }

    private int littleEndianShort(byte[] bytes, int offset) {
        if (offset < 0 || offset + 2 > bytes.length) {
            return -1;
        }
        return Byte.toUnsignedInt(bytes[offset]) | (Byte.toUnsignedInt(bytes[offset + 1]) << 8);
    }

    private int littleEndianInt(byte[] bytes, int offset) {
        if (offset < 0 || offset + 4 > bytes.length) {
            return -1;
        }
        return Byte.toUnsignedInt(bytes[offset])
                | (Byte.toUnsignedInt(bytes[offset + 1]) << 8)
                | (Byte.toUnsignedInt(bytes[offset + 2]) << 16)
                | (Byte.toUnsignedInt(bytes[offset + 3]) << 24);
    }

    private void readFully(FileChannel channel, ByteBuffer buffer, long position)
            throws IOException {
        while (buffer.hasRemaining()) {
            int read = channel.read(buffer, position);
            if (read < 0) {
                throw new ZipException("ZIP 文件意外结束");
            }
            position += read;
        }
    }

    private void validateCompression(ZipEntry entry) {
        if (entry.getMethod() != ZipEntry.STORED && entry.getMethod() != ZipEntry.DEFLATED) {
            throw invalid("信息包包含不支持的压缩算法");
        }
    }

    private String validateEntryName(String rawName, boolean directory) {
        if (StringUtils.isBlank(rawName)
                || rawName.startsWith("/")
                || rawName.startsWith("\\")
                || rawName.contains("\\")
                || rawName.indexOf('\0') >= 0
                || rawName.matches("^[A-Za-z]:.*")) {
            throw invalid("信息包包含不安全路径");
        }
        String name =
                directory && rawName.endsWith("/")
                        ? rawName.substring(0, rawName.length() - 1)
                        : rawName;
        if (name.isEmpty() || name.endsWith("/")) {
            throw invalid("信息包包含不安全路径：" + rawName);
        }
        for (String segment : name.split("/", -1)) {
            if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment)) {
                throw invalid("信息包包含不安全路径：" + rawName);
            }
        }
        return name;
    }

    private ExtractedFile extractFile(
            InputStream input, String logicalPath, Path tempDirectory, long currentTotal)
            throws IOException {
        Path tempFile = Files.createTempFile(tempDirectory, "entry-", ".bin");
        MessageDigest digest = sha256();
        long size = 0;
        byte[] buffer = new byte[COPY_BUFFER_SIZE];
        try (InputStream digestInput = new DigestInputStream(input, digest);
                var output = Files.newOutputStream(tempFile)) {
            int read;
            while ((read = digestInput.read(buffer)) != -1) {
                size += read;
                if (size > MAX_SINGLE_FILE_BYTES) {
                    throw invalid("信息包内单个文件不能超过 50 MiB：" + logicalPath);
                }
                if (currentTotal + size > MAX_UNCOMPRESSED_BYTES) {
                    throw invalid("信息包解压总量不能超过 200 MiB");
                }
                output.write(buffer, 0, read);
            }
        }
        return new ExtractedFile(tempFile, size, hex(digest.digest()));
    }

    private MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JDK 缺少 SHA-256", exception);
        }
    }

    private String hex(byte[] bytes) {
        return java.util.HexFormat.of().formatHex(bytes);
    }

    private BadRequestException invalid(String message) {
        return new BadRequestException(message);
    }

    private record ExtractedFile(Path path, long size, String sha256) {}

    record ExtractedEntry(
            String logicalPath,
            boolean directory,
            @Nullable Path path,
            long size,
            @Nullable String sha256) {}

    record ExtractedPackage(
            Map<String, ExtractedEntry> entries, long totalBytes, int zipEntryCount) {}
}
