package github.luckygc.am.module.intake.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import github.luckygc.am.common.exception.BadRequestException;
import github.luckygc.am.module.intake.ArchiveIntakeValidationCategory;
import github.luckygc.am.module.intake.ArchiveIntakeValidationOutcome;

import tools.jackson.databind.json.JsonMapper;

@Component
public class ArchiveIntakePackageParser {

    public static final int MAX_COMPRESSED_BYTES = 50 * 1024 * 1024;
    static final int MAX_STANDARD_FILE_BYTES = 2 * 1024 * 1024;
    static final int MAX_ITEMS = 100;
    static final int MAX_CONTENT_FILES = 500;
    static final int MAX_SINGLE_FILE_BYTES = 50 * 1024 * 1024;
    static final long MAX_UNCOMPRESSED_BYTES = 200L * 1024 * 1024;
    static final int MAX_ZIP_ENTRIES = 1000;

    private static final String EXPLANATION_NAME = "说明文件.TXT";
    private static final String CATALOG_NAME = "目录文件.XML";
    private static final String OTHER_DIRECTORY = "其他";
    private static final Pattern METADATA_FILE_NAME = Pattern.compile("^电子档案.*元数据\\.XML$");
    private static final Pattern XML_ENCODING =
            Pattern.compile(
                    "<\\?xml[^>]*\\bencoding\\s*=\\s*['\"]([^'\"]+)['\"]",
                    Pattern.CASE_INSENSITIVE);
    private static final Set<String> ALLOWED_XML_ENCODINGS =
            Set.of("UTF-8", "UTF8", "GB18030", "GB2312");
    private static final int ZIP_END_SIGNATURE = 0x06054b50;
    private static final int ZIP_CENTRAL_SIGNATURE = 0x02014b50;
    private static final int ZIP64_SENTINEL = 0xffff;
    private static final int COPY_BUFFER_SIZE = 8192;

    public ArchiveIntakePackageParser(JsonMapper ignored) {}

    public ArchiveIntakeManifest parse(byte[] packageBytes) {
        Path packagePath;
        try {
            packagePath = Files.createTempFile("archive-intake-upload-", ".zip");
            Files.write(packagePath, packageBytes);
        } catch (IOException exception) {
            throw invalid("信息包临时文件写入失败");
        }
        try {
            return parsePackage(packagePath);
        } finally {
            try {
                Files.deleteIfExists(packagePath);
            } catch (IOException ignored) {
                // 测试便利入口的临时上传文件尽力清理。
            }
        }
    }

    public ArchiveIntakeManifest parse(Path packagePath) {
        return parsePackage(packagePath);
    }

    private ArchiveIntakeManifest parsePackage(Path packagePath) {
        long compressedBytes;
        try {
            compressedBytes = Files.size(packagePath);
        } catch (IOException exception) {
            throw invalid("信息包读取失败");
        }
        if (compressedBytes == 0) {
            throw invalid("信息包不能为空");
        }
        if (compressedBytes > MAX_COMPRESSED_BYTES) {
            throw invalid("信息包压缩数据不能超过 50 MiB");
        }
        inspectZipHeaders(packagePath, compressedBytes);

        Path tempDirectory;
        try {
            tempDirectory = Files.createTempDirectory("archive-intake-");
        } catch (IOException exception) {
            throw invalid("信息包临时目录创建失败");
        }
        try {
            return parseIntoTempDirectory(packagePath, tempDirectory);
        } catch (RuntimeException exception) {
            deleteTempDirectory(tempDirectory);
            throw exception;
        }
    }

    private ArchiveIntakeManifest parseIntoTempDirectory(Path packagePath, Path tempDirectory) {
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

        NormalizedTree tree = normalizeRoot(extracted);
        ExtractedEntry explanation = requiredFile(tree.entries(), EXPLANATION_NAME);
        ExtractedEntry catalog = requiredFile(tree.entries(), CATALOG_NAME);
        requireStandardFileSize(explanation);
        requireStandardFileSize(catalog);

        String explanationText = readText(explanation.path(), EXPLANATION_NAME);
        ExplanationData explanationData = parseExplanation(explanationText);
        CatalogData catalogData = parseCatalog(catalog.path());
        if (explanationData.archiveCount() != catalogData.items().size()) {
            throw invalid(
                    "说明文件.TXT 的档案数量与目录文件.XML 条目数不一致："
                            + explanationData.archiveCount()
                            + " != "
                            + catalogData.items().size());
        }
        PackageTreeData packageTree = parsePackageTree(tree.entries(), catalogData.items());
        List<ArchiveIntakeValidationResult> validationResults =
                buildValidationResults(
                        explanationData.missingOptionalFields(),
                        catalogData.missingPageCount(),
                        packageTree.contentFileCount(),
                        totalBytes);

        PackageStatistics statistics =
                new PackageStatistics(
                        catalogData.items().size(),
                        packageTree.contentFileCount(),
                        totalBytes,
                        zipEntryCount);
        List<ArchiveIntakeManifestItem> items =
                mergeCatalogAndPackageTree(catalogData.items(), packageTree.archives());
        return new ArchiveIntakeManifest(
                "DAT93_ITEM",
                explanationData.packageCode(),
                items,
                explanationText,
                statistics,
                validationResults,
                tempDirectory);
    }

    private void inspectZipHeaders(Path packagePath, long compressedBytes) {
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

    private NormalizedTree normalizeRoot(Map<String, ExtractedEntry> entries) {
        boolean hasRootExplanation = entries.containsKey(EXPLANATION_NAME);
        boolean hasRootCatalog = entries.containsKey(CATALOG_NAME);
        String prefix = "";
        if (!hasRootExplanation && !hasRootCatalog) {
            Set<String> topSegments = new HashSet<>();
            for (String path : entries.keySet()) {
                int slash = path.indexOf('/');
                topSegments.add(slash < 0 ? path : path.substring(0, slash));
            }
            if (topSegments.size() != 1) {
                throw invalid("信息包根目录必须直放标准内容或仅包含一个顶层目录");
            }
            prefix = topSegments.iterator().next() + "/";
        } else if (!hasRootExplanation || !hasRootCatalog) {
            throw invalid("说明文件.TXT 和目录文件.XML 必须位于同一信息包根目录");
        }

        Map<String, ExtractedEntry> normalized = new LinkedHashMap<>();
        for (ExtractedEntry entry : entries.values()) {
            if (!prefix.isEmpty()
                    && entry.logicalPath().equals(prefix.substring(0, prefix.length() - 1))) {
                if (!entry.directory()) {
                    throw invalid("信息包唯一顶层路径必须是目录");
                }
                continue;
            }
            if (!entry.logicalPath().startsWith(prefix)) {
                throw invalid("信息包顶层目录之外存在非法文件");
            }
            String relativePath = entry.logicalPath().substring(prefix.length());
            if (relativePath.isEmpty()) {
                continue;
            }
            normalized.put(
                    relativePath,
                    new ExtractedEntry(
                            relativePath,
                            entry.directory(),
                            entry.path(),
                            entry.size(),
                            entry.sha256()));
        }
        return new NormalizedTree(Collections.unmodifiableMap(normalized));
    }

    private ExtractedEntry requiredFile(Map<String, ExtractedEntry> entries, String name) {
        ExtractedEntry entry = entries.get(name);
        if (entry == null || entry.directory()) {
            throw invalid("信息包缺少根目录 " + name);
        }
        return entry;
    }

    private void requireStandardFileSize(ExtractedEntry entry) {
        if (entry.size() > MAX_STANDARD_FILE_BYTES) {
            throw invalid(entry.logicalPath() + " 不能超过 2 MiB");
        }
    }

    private String readText(@Nullable Path path, String name) {
        byte[] bytes = readBytes(path, name);
        if (startsWith(bytes, new byte[] {(byte) 0xff, (byte) 0xfe})
                || startsWith(bytes, new byte[] {(byte) 0xfe, (byte) 0xff})) {
            throw invalid(name + " 编码非法，仅支持 UTF-8、GB18030 或 GB2312");
        }
        if (startsWith(bytes, new byte[] {(byte) 0xef, (byte) 0xbb, (byte) 0xbf})) {
            bytes = java.util.Arrays.copyOfRange(bytes, 3, bytes.length);
        }
        for (Charset charset :
                List.of(
                        StandardCharsets.UTF_8,
                        Charset.forName("GB18030"),
                        Charset.forName("GB2312"))) {
            try {
                String result =
                        charset.newDecoder()
                                .onMalformedInput(CodingErrorAction.REPORT)
                                .onUnmappableCharacter(CodingErrorAction.REPORT)
                                .decode(ByteBuffer.wrap(bytes))
                                .toString();
                if (result.indexOf('\0') < 0) {
                    return result;
                }
            } catch (CharacterCodingException ignored) {
                // 尝试规范允许的下一种编码。
            }
        }
        throw invalid(name + " 编码非法，仅支持 UTF-8、GB18030 或 GB2312");
    }

    private ExplanationData parseExplanation(String text) {
        for (String field : List.of("移交单位", "内容描述", "起止档号", "档案数量", "软硬件环境")) {
            if (extractExplanationValue(text, field) == null) {
                throw invalid("说明文件.TXT 缺少必填项：" + field);
            }
        }
        int archiveCount;
        String countValue = requireNonNull(extractExplanationValue(text, "档案数量"));
        try {
            archiveCount = Integer.parseInt(countValue);
        } catch (NumberFormatException exception) {
            throw invalid("说明文件.TXT 的档案数量必须是整数");
        }
        if (archiveCount < 1 || archiveCount > MAX_ITEMS) {
            throw invalid("说明文件.TXT 的档案数量必须在 1 到 100 之间");
        }
        boolean missingOptionalFields =
                List.of("离线载体参数", "载体编号", "载体数量", "制作单位", "检查单位").stream()
                        .anyMatch(field -> extractExplanationValue(text, field) == null);
        return new ExplanationData(
                extractExplanationValue(text, "载体编号"), archiveCount, missingOptionalFields);
    }

    private @Nullable String extractExplanationValue(String text, String field) {
        Matcher matcher =
                Pattern.compile(
                                "(?m)^\\s*"
                                        + Pattern.quote(field)
                                        + "\\s*[:：=]\\s*([^\\r\\n]+)\\s*$")
                        .matcher(text);
        return matcher.find() ? StringUtils.trimToNull(matcher.group(1)) : null;
    }

    private CatalogData parseCatalog(@Nullable Path path) {
        Document document = parseXml(path, CATALOG_NAME);
        Element root = document.getDocumentElement();
        if (root == null || !"文件目录".equals(root.getTagName())) {
            throw invalid("目录文件.XML 根元素必须为 文件目录");
        }
        List<Element> fileElements =
                directChildren(root, element -> "文件".equals(element.getTagName()));
        for (Element child : directChildren(root, ignored -> true)) {
            if (!"文件".equals(child.getTagName())) {
                throw invalid("目录文件.XML 包含非法结构：" + child.getTagName());
            }
        }
        if (fileElements.isEmpty()) {
            throw invalid("目录文件.XML 至少需要包含一个 文件 条目");
        }
        if (fileElements.size() > MAX_ITEMS) {
            throw invalid("目录文件.XML 最多包含 100 个档案条目");
        }

        List<CatalogItem> items = new ArrayList<>();
        Set<String> archiveNumbers = new HashSet<>();
        boolean missingPageCount = false;
        for (int index = 0; index < fileElements.size(); index++) {
            Element element = fileElements.get(index);
            Set<String> allowedFields =
                    Set.of("顺序号", "档号", "责任者", "题名", "日期", "保管期限", "密级", "页数", "备注");
            for (Element child : directChildren(element, ignored -> true)) {
                if (!allowedFields.contains(child.getTagName()) || hasElementChild(child)) {
                    throw invalid(
                            "目录文件.XML 第 " + (index + 1) + " 个 文件 包含非法结构：" + child.getTagName());
                }
            }
            String sequenceNumber = requiredChildText(element, "顺序号", index);
            String archiveNo = requiredChildText(element, "档号", index);
            String responsible = requiredChildText(element, "责任者", index);
            String title = requiredChildText(element, "题名", index);
            String date = requiredChildText(element, "日期", index);
            String retentionPeriod = requiredChildText(element, "保管期限", index);
            String securityLevel = requiredChildText(element, "密级", index);
            String remarks = requiredChildElementText(element, "备注", index);
            String pageCountText = optionalChildText(element, "页数", index);
            Integer pageCount = null;
            if (pageCountText == null) {
                missingPageCount = true;
            } else {
                try {
                    pageCount = Integer.valueOf(pageCountText);
                } catch (NumberFormatException exception) {
                    throw invalid("目录文件.XML 第 " + (index + 1) + " 个 文件 的页数必须是整数");
                }
                if (pageCount < 0) {
                    throw invalid("目录文件.XML 第 " + (index + 1) + " 个 文件 的页数不能为负数");
                }
            }
            if (!archiveNumbers.add(archiveNo)) {
                throw invalid("目录文件.XML 包含重复档号：" + archiveNo);
            }
            items.add(
                    new CatalogItem(
                            sequenceNumber,
                            archiveNo,
                            responsible,
                            title,
                            date,
                            retentionPeriod,
                            securityLevel,
                            pageCount,
                            remarks));
        }
        return new CatalogData(List.copyOf(items), missingPageCount);
    }

    private Document parseXml(@Nullable Path path, String displayName) {
        if (path == null) {
            throw invalid(displayName + " 不存在");
        }
        validateXmlEncoding(path, displayName);
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            try (InputStream input = Files.newInputStream(path)) {
                return factory.newDocumentBuilder().parse(input);
            }
        } catch (ParserConfigurationException exception) {
            throw invalid("XML 安全解析器初始化失败");
        } catch (SAXException exception) {
            throw invalid(displayName + " 不是合法安全 XML，禁止 DTD 和外部实体");
        } catch (IOException exception) {
            throw invalid(displayName + " 读取失败");
        }
    }

    private void validateXmlEncoding(Path path, String displayName) {
        byte[] prefix;
        try (InputStream input = Files.newInputStream(path)) {
            prefix = input.readNBytes(512);
        } catch (IOException exception) {
            throw invalid(displayName + " 读取失败");
        }
        String declaration = new String(prefix, StandardCharsets.ISO_8859_1);
        Matcher matcher = XML_ENCODING.matcher(declaration);
        if (matcher.find()
                && !ALLOWED_XML_ENCODINGS.contains(matcher.group(1).toUpperCase(Locale.ROOT))) {
            throw invalid(displayName + " 编码非法，仅支持 UTF-8、GB18030 或 GB2312");
        }
        if (startsWith(prefix, new byte[] {(byte) 0xff, (byte) 0xfe})
                || startsWith(prefix, new byte[] {(byte) 0xfe, (byte) 0xff})) {
            throw invalid(displayName + " 编码非法，仅支持 UTF-8、GB18030 或 GB2312");
        }
    }

    private PackageTreeData parsePackageTree(
            Map<String, ExtractedEntry> entries, List<CatalogItem> catalogItems) {
        Set<String> allowedRootDirectories = new HashSet<>();
        Map<String, List<ExtractedEntry>> archiveFiles = new LinkedHashMap<>();
        for (ExtractedEntry entry : entries.values()) {
            String path = entry.logicalPath();
            if (EXPLANATION_NAME.equals(path) || CATALOG_NAME.equals(path)) {
                continue;
            }
            String[] parts = path.split("/");
            if (parts.length == 1) {
                if (!entry.directory()) {
                    throw invalid("信息包根目录包含非法文件：" + path);
                }
                allowedRootDirectories.add(parts[0]);
                continue;
            }
            allowedRootDirectories.add(parts[0]);
            if (OTHER_DIRECTORY.equals(parts[0]) || entry.directory()) {
                continue;
            }
            if (parts.length < 4) {
                throw invalid("信息包文件未归属到档案目录：" + path);
            }
            String archiveDirectory =
                    String.join("/", java.util.Arrays.copyOf(parts, parts.length - 1));
            archiveFiles.computeIfAbsent(archiveDirectory, ignored -> new ArrayList<>()).add(entry);
        }
        allowedRootDirectories.remove(OTHER_DIRECTORY);
        if (allowedRootDirectories.isEmpty()) {
            throw invalid("信息包至少需要包含一个全宗目录");
        }
        if (archiveFiles.isEmpty()) {
            throw invalid("信息包未包含电子档案目录");
        }

        Map<String, CatalogItem> catalogByArchiveNo = new HashMap<>();
        for (CatalogItem item : catalogItems) {
            catalogByArchiveNo.put(item.archiveNo(), item);
        }
        Map<String, ArchiveDirectory> archives = new HashMap<>();
        int contentFileCount = 0;
        for (Map.Entry<String, List<ExtractedEntry>> archive : archiveFiles.entrySet()) {
            String[] parts = archive.getKey().split("/");
            String archiveNo = parts[parts.length - 1];
            if (!catalogByArchiveNo.containsKey(archiveNo)) {
                throw invalid("存在未被目录文件.XML 著录的档案目录：" + archive.getKey());
            }
            if (archives.containsKey(archiveNo)) {
                throw invalid("档号关联到多个档案目录：" + archiveNo);
            }
            List<ParsedFile> metadataFiles = new ArrayList<>();
            List<ParsedFile> contentFiles = new ArrayList<>();
            for (ExtractedEntry file : archive.getValue()) {
                String fileName =
                        file.logicalPath().substring(file.logicalPath().lastIndexOf('/') + 1);
                ParsedFile parsedFile =
                        new ParsedFile(
                                fileName,
                                file.logicalPath(),
                                file.size(),
                                requireNonNull(file.sha256()),
                                requireNonNull(file.path()));
                if (METADATA_FILE_NAME.matcher(fileName).matches()) {
                    parseXml(file.path(), file.logicalPath());
                    metadataFiles.add(parsedFile);
                } else {
                    contentFileCount++;
                    if (contentFileCount > MAX_CONTENT_FILES) {
                        throw invalid("信息包内容文件不能超过 500 个");
                    }
                    contentFiles.add(parsedFile);
                }
            }
            if (metadataFiles.isEmpty()) {
                throw invalid("档案目录缺少电子档案元数据 XML：" + archive.getKey());
            }
            if (contentFiles.isEmpty()) {
                throw invalid("档案目录缺少内容文件：" + archive.getKey());
            }
            archives.put(
                    archiveNo,
                    new ArchiveDirectory(
                            parts[0],
                            parts[1],
                            List.copyOf(metadataFiles),
                            List.copyOf(contentFiles)));
        }
        for (CatalogItem item : catalogItems) {
            if (!archives.containsKey(item.archiveNo())) {
                throw invalid("目录文件.XML 中的档号缺少同名档案目录：" + item.archiveNo());
            }
        }
        return new PackageTreeData(Map.copyOf(archives), contentFileCount);
    }

    private List<ArchiveIntakeManifestItem> mergeCatalogAndPackageTree(
            List<CatalogItem> catalogItems, Map<String, ArchiveDirectory> archives) {
        List<ArchiveIntakeManifestItem> result = new ArrayList<>();
        for (CatalogItem catalog : catalogItems) {
            ArchiveDirectory archive = archives.get(catalog.archiveNo());
            Map<String, @Nullable Object> dynamicFields = new LinkedHashMap<>();
            dynamicFields.put("责任者", catalog.responsible());
            dynamicFields.put("题名", catalog.title());
            dynamicFields.put("日期", catalog.date());
            dynamicFields.put("页数", catalog.pageCount());
            dynamicFields.put("备注", catalog.remarks());
            result.add(
                    new ArchiveIntakeManifestItem(
                            archive.fondsCode(),
                            archive.categoryCode(),
                            catalog.archiveNo(),
                            extractYear(catalog.date()),
                            null,
                            null,
                            Collections.unmodifiableMap(dynamicFields),
                            Map.of(),
                            catalog.sequenceNumber(),
                            catalog.responsible(),
                            catalog.title(),
                            catalog.date(),
                            catalog.retentionPeriod(),
                            catalog.securityLevel(),
                            catalog.pageCount(),
                            catalog.remarks(),
                            archive.metadataFiles(),
                            archive.contentFiles()));
        }
        return List.copyOf(result);
    }

    private List<ArchiveIntakeValidationResult> buildValidationResults(
            boolean missingExplanationOptionalFields,
            boolean missingPageCount,
            int contentFileCount,
            long totalBytes) {
        List<ArchiveIntakeValidationResult> results = new ArrayList<>();
        results.add(pass("DAT70_STRUCTURE", ArchiveIntakeValidationCategory.INTEGRITY, "标准目录结构完整"));
        results.add(
                pass(
                        "DAT70_XML_READABLE",
                        ArchiveIntakeValidationCategory.USABILITY,
                        "目录及元数据 XML 可安全解析"));
        results.add(
                pass(
                        "DAT70_REQUIRED_FIELDS",
                        ArchiveIntakeValidationCategory.INTEGRITY,
                        "目录必填项完整"));
        results.add(
                pass(
                        "DAT70_ARCHIVE_ASSOCIATION",
                        ArchiveIntakeValidationCategory.INTEGRITY,
                        "档号与档案目录唯一关联"));
        results.add(
                pass(
                        "DAT70_COUNTS",
                        ArchiveIntakeValidationCategory.INTEGRITY,
                        "档案及内容文件数量校验通过，共 " + contentFileCount + " 个内容文件"));
        results.add(
                pass(
                        "DAT70_BYTE_COUNT",
                        ArchiveIntakeValidationCategory.INTEGRITY,
                        "解压总字节数校验通过，共 " + totalBytes + " 字节"));
        results.add(
                pass(
                        "DAT70_COMPRESSION",
                        ArchiveIntakeValidationCategory.USABILITY,
                        "压缩算法受支持且条目未加密"));
        if (missingPageCount) {
            results.add(
                    warning(
                            "DAT93_PAGE_COUNT",
                            ArchiveIntakeValidationCategory.INTEGRITY,
                            "目录文件.XML 未提供部分档案的页数，需后续补充或核验"));
        }
        if (missingExplanationOptionalFields) {
            results.add(
                    warning(
                            "DAT93_EXPLANATION_FIELDS",
                            ArchiveIntakeValidationCategory.INTEGRITY,
                            "说明文件缺少部分离线载体或检查信息"));
        }
        results.add(
                manual(
                        "DAT70_SOURCE_FIXITY",
                        ArchiveIntakeValidationCategory.AUTHENTICITY,
                        "需要移交前摘要或来源系统证据复核"));
        results.add(
                manual(
                        "DAT70_TRUSTED_SIGNATURE",
                        ArchiveIntakeValidationCategory.AUTHENTICITY,
                        "电子签名、印章和时间戳需要可信验证能力复核"));
        results.add(
                manual(
                        "DAT70_CONTENT_REVIEW",
                        ArchiveIntakeValidationCategory.USABILITY,
                        "内容文件需人工打开浏览复核"));
        results.add(
                manual(
                        "DAT70_ANTIVIRUS",
                        ArchiveIntakeValidationCategory.SECURITY,
                        "病毒检测需要外部杀毒引擎复核"));
        results.add(
                manual(
                        "DAT70_PHYSICAL_CARRIER",
                        ArchiveIntakeValidationCategory.SECURITY,
                        "离线载体外观和读取速度需要实物复核"));
        return List.copyOf(results);
    }

    private ArchiveIntakeValidationResult pass(
            String code, ArchiveIntakeValidationCategory category, String description) {
        return new ArchiveIntakeValidationResult(
                code, category, ArchiveIntakeValidationOutcome.PASSED, description);
    }

    private ArchiveIntakeValidationResult warning(
            String code, ArchiveIntakeValidationCategory category, String description) {
        return new ArchiveIntakeValidationResult(
                code, category, ArchiveIntakeValidationOutcome.WARNING, description);
    }

    private ArchiveIntakeValidationResult manual(
            String code, ArchiveIntakeValidationCategory category, String description) {
        return new ArchiveIntakeValidationResult(
                code, category, ArchiveIntakeValidationOutcome.MANUAL_REVIEW, description);
    }

    private @Nullable Integer extractYear(String date) {
        Matcher matcher = Pattern.compile("^(\\d{4})").matcher(date);
        return matcher.find() ? Integer.valueOf(matcher.group(1)) : null;
    }

    private String requiredChildText(Element parent, String name, int itemIndex) {
        String value = requiredChildElementText(parent, name, itemIndex);
        if (StringUtils.isBlank(value)) {
            throw invalid("目录文件.XML 第 " + (itemIndex + 1) + " 个 文件 的" + name + "不能为空");
        }
        return value;
    }

    private String requiredChildElementText(Element parent, String name, int itemIndex) {
        List<Element> children =
                directChildren(parent, element -> name.equals(element.getTagName()));
        if (children.size() != 1) {
            throw invalid("目录文件.XML 第 " + (itemIndex + 1) + " 个 文件 必须且只能包含一个" + name);
        }
        return StringUtils.trim(children.getFirst().getTextContent());
    }

    private @Nullable String optionalChildText(Element parent, String name, int itemIndex) {
        List<Element> children =
                directChildren(parent, element -> name.equals(element.getTagName()));
        if (children.size() > 1) {
            throw invalid("目录文件.XML 第 " + (itemIndex + 1) + " 个 文件 不能重复包含" + name);
        }
        if (children.isEmpty()) {
            return null;
        }
        return StringUtils.trimToNull(children.getFirst().getTextContent());
    }

    private List<Element> directChildren(Element parent, Predicate<Element> predicate) {
        List<Element> result = new ArrayList<>();
        for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element element && predicate.test(element)) {
                result.add(element);
            }
        }
        return result;
    }

    private boolean hasElementChild(Element parent) {
        for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element) {
                return true;
            }
        }
        return false;
    }

    private byte[] readBytes(@Nullable Path path, String name) {
        if (path == null) {
            throw invalid(name + " 不存在");
        }
        try {
            return Files.readAllBytes(path);
        } catch (IOException exception) {
            throw invalid(name + " 读取失败");
        }
    }

    private boolean startsWith(byte[] bytes, byte[] prefix) {
        if (bytes.length < prefix.length) {
            return false;
        }
        for (int index = 0; index < prefix.length; index++) {
            if (bytes[index] != prefix[index]) {
                return false;
            }
        }
        return true;
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

    private <T> T requireNonNull(@Nullable T value) {
        if (value == null) {
            throw new IllegalStateException("解析后的文件信息不完整");
        }
        return value;
    }

    private BadRequestException invalid(String message) {
        return new BadRequestException(message);
    }

    private static void deleteTempDirectory(@Nullable Path directory) {
        if (directory == null) {
            return;
        }
        try (var paths = Files.list(directory)) {
            paths.forEach(
                    path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ignored) {
                            // 尽力清理；临时目录由操作系统后续回收。
                        }
                    });
        } catch (IOException ignored) {
            // 目录可能已被清理。
        }
        try {
            Files.deleteIfExists(directory);
        } catch (IOException ignored) {
            // 尽力清理；临时目录由操作系统后续回收。
        }
    }

    public static final class ArchiveIntakeManifest implements AutoCloseable {

        private final @Nullable String formatVersion;
        private final @Nullable String packageCode;
        private final List<ArchiveIntakeManifestItem> items;
        private final @Nullable String explanationText;
        private final @Nullable PackageStatistics statistics;
        private final List<ArchiveIntakeValidationResult> validationResults;
        private final @Nullable Path tempDirectory;

        public ArchiveIntakeManifest(
                @Nullable String formatVersion,
                @Nullable String packageCode,
                @Nullable List<ArchiveIntakeManifestItem> items) {
            this(formatVersion, packageCode, items, null, null, List.of(), null);
        }

        private ArchiveIntakeManifest(
                @Nullable String formatVersion,
                @Nullable String packageCode,
                @Nullable List<ArchiveIntakeManifestItem> items,
                @Nullable String explanationText,
                @Nullable PackageStatistics statistics,
                List<ArchiveIntakeValidationResult> validationResults,
                @Nullable Path tempDirectory) {
            this.formatVersion = formatVersion;
            this.packageCode = packageCode;
            this.items = items == null ? List.of() : List.copyOf(items);
            this.explanationText = explanationText;
            this.statistics = statistics;
            this.validationResults = List.copyOf(validationResults);
            this.tempDirectory = tempDirectory;
        }

        public @Nullable String formatVersion() {
            return formatVersion;
        }

        public @Nullable String packageCode() {
            return packageCode;
        }

        public List<ArchiveIntakeManifestItem> items() {
            return items;
        }

        public @Nullable String explanationText() {
            return explanationText;
        }

        public @Nullable PackageStatistics statistics() {
            return statistics;
        }

        public List<ArchiveIntakeValidationResult> validationResults() {
            return validationResults;
        }

        @Override
        public void close() {
            deleteTempDirectory(tempDirectory);
        }
    }

    public static final class ArchiveIntakeManifestItem {

        private final @Nullable String fondsCode;
        private final @Nullable String categoryCode;
        private final @Nullable String archiveNo;
        private final @Nullable Integer archiveYear;
        private final @Nullable Long securityLevelId;
        private final @Nullable Long retentionPeriodId;
        private final Map<String, @Nullable Object> dynamicFields;
        private final Map<String, @Nullable Object> physicalFields;
        private final @Nullable String sequenceNumber;
        private final @Nullable String responsible;
        private final @Nullable String title;
        private final @Nullable String date;
        private final @Nullable String retentionPeriod;
        private final @Nullable String securityLevel;
        private final @Nullable Integer pageCount;
        private final @Nullable String remarks;
        private final List<ParsedFile> metadataFiles;
        private final List<ParsedFile> contentFiles;

        public ArchiveIntakeManifestItem(
                @Nullable String fondsCode,
                @Nullable String categoryCode,
                @Nullable String archiveNo,
                @Nullable Integer archiveYear,
                @Nullable Long securityLevelId,
                @Nullable Long retentionPeriodId,
                @Nullable Map<String, @Nullable Object> dynamicFields,
                @Nullable Map<String, @Nullable Object> physicalFields) {
            this(
                    fondsCode,
                    categoryCode,
                    archiveNo,
                    archiveYear,
                    securityLevelId,
                    retentionPeriodId,
                    dynamicFields,
                    physicalFields,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    List.of(),
                    List.of());
        }

        private ArchiveIntakeManifestItem(
                @Nullable String fondsCode,
                @Nullable String categoryCode,
                @Nullable String archiveNo,
                @Nullable Integer archiveYear,
                @Nullable Long securityLevelId,
                @Nullable Long retentionPeriodId,
                @Nullable Map<String, @Nullable Object> dynamicFields,
                @Nullable Map<String, @Nullable Object> physicalFields,
                @Nullable String sequenceNumber,
                @Nullable String responsible,
                @Nullable String title,
                @Nullable String date,
                @Nullable String retentionPeriod,
                @Nullable String securityLevel,
                @Nullable Integer pageCount,
                @Nullable String remarks,
                List<ParsedFile> metadataFiles,
                List<ParsedFile> contentFiles) {
            this.fondsCode = fondsCode;
            this.categoryCode = categoryCode;
            this.archiveNo = archiveNo;
            this.archiveYear = archiveYear;
            this.securityLevelId = securityLevelId;
            this.retentionPeriodId = retentionPeriodId;
            this.dynamicFields =
                    dynamicFields == null ? Map.of() : Collections.unmodifiableMap(dynamicFields);
            this.physicalFields =
                    physicalFields == null ? Map.of() : Collections.unmodifiableMap(physicalFields);
            this.sequenceNumber = sequenceNumber;
            this.responsible = responsible;
            this.title = title;
            this.date = date;
            this.retentionPeriod = retentionPeriod;
            this.securityLevel = securityLevel;
            this.pageCount = pageCount;
            this.remarks = remarks;
            this.metadataFiles = List.copyOf(metadataFiles);
            this.contentFiles = List.copyOf(contentFiles);
        }

        public @Nullable String fondsCode() {
            return fondsCode;
        }

        public @Nullable String categoryCode() {
            return categoryCode;
        }

        public @Nullable String archiveNo() {
            return archiveNo;
        }

        public @Nullable Integer archiveYear() {
            return archiveYear;
        }

        public @Nullable Long securityLevelId() {
            return securityLevelId;
        }

        public @Nullable Long retentionPeriodId() {
            return retentionPeriodId;
        }

        public Map<String, @Nullable Object> dynamicFields() {
            return dynamicFields;
        }

        public Map<String, @Nullable Object> physicalFields() {
            return physicalFields;
        }

        public @Nullable String sequenceNumber() {
            return sequenceNumber;
        }

        public @Nullable String responsible() {
            return responsible;
        }

        public @Nullable String title() {
            return title;
        }

        public @Nullable String date() {
            return date;
        }

        public @Nullable String retentionPeriod() {
            return retentionPeriod;
        }

        public @Nullable String securityLevel() {
            return securityLevel;
        }

        public @Nullable Integer pageCount() {
            return pageCount;
        }

        public @Nullable String remarks() {
            return remarks;
        }

        public List<ParsedFile> metadataFiles() {
            return metadataFiles;
        }

        public List<ParsedFile> contentFiles() {
            return contentFiles;
        }
    }

    public record ParsedFile(
            String originalName,
            String logicalPath,
            long size,
            String sha256,
            Path temporaryPath) {}

    public record PackageStatistics(
            int itemCount, int contentFileCount, long uncompressedBytes, int zipEntryCount) {}

    public record ArchiveIntakeValidationResult(
            String code,
            ArchiveIntakeValidationCategory category,
            ArchiveIntakeValidationOutcome outcome,
            String message) {}

    private record ExtractedFile(Path path, long size, String sha256) {}

    private record ExtractedEntry(
            String logicalPath,
            boolean directory,
            @Nullable Path path,
            long size,
            @Nullable String sha256) {}

    private record NormalizedTree(Map<String, ExtractedEntry> entries) {}

    private record CatalogData(List<CatalogItem> items, boolean missingPageCount) {}

    private record ExplanationData(
            @Nullable String packageCode, int archiveCount, boolean missingOptionalFields) {}

    private record CatalogItem(
            String sequenceNumber,
            String archiveNo,
            String responsible,
            String title,
            String date,
            String retentionPeriod,
            String securityLevel,
            @Nullable Integer pageCount,
            String remarks) {}

    private record ArchiveDirectory(
            String fondsCode,
            String categoryCode,
            List<ParsedFile> metadataFiles,
            List<ParsedFile> contentFiles) {}

    private record PackageTreeData(Map<String, ArchiveDirectory> archives, int contentFileCount) {}
}
