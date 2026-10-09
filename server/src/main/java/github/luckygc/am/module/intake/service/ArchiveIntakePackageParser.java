package github.luckygc.am.module.intake.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

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

    private final ArchiveIntakeZipExtractor zipExtractor = new ArchiveIntakeZipExtractor();
    private final ArchiveIntakeManifestAssembler manifestAssembler =
            new ArchiveIntakeManifestAssembler(new ArchiveIntakeDocumentParser());

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
        zipExtractor.inspectHeaders(packagePath, compressedBytes);

        Path tempDirectory;
        try {
            tempDirectory = Files.createTempDirectory("archive-intake-");
        } catch (IOException exception) {
            throw invalid("信息包临时目录创建失败");
        }
        try {
            var extracted = zipExtractor.extract(packagePath, tempDirectory);
            return manifestAssembler.assemble(extracted, tempDirectory);
        } catch (RuntimeException exception) {
            deleteTempDirectory(tempDirectory);
            throw exception;
        }
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

        ArchiveIntakeManifest(
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

        ArchiveIntakeManifestItem(
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
}
