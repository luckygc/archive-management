package github.luckygc.am.module.intake.service;

import static github.luckygc.am.module.intake.service.ArchiveIntakePackageParser.MAX_CONTENT_FILES;
import static github.luckygc.am.module.intake.service.ArchiveIntakePackageParser.MAX_STANDARD_FILE_BYTES;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

import github.luckygc.am.common.exception.BadRequestException;
import github.luckygc.am.module.intake.ArchiveIntakeValidationCategory;
import github.luckygc.am.module.intake.ArchiveIntakeValidationOutcome;
import github.luckygc.am.module.intake.service.ArchiveIntakeDocumentParser.CatalogData;
import github.luckygc.am.module.intake.service.ArchiveIntakeDocumentParser.CatalogItem;
import github.luckygc.am.module.intake.service.ArchiveIntakeDocumentParser.ExplanationData;
import github.luckygc.am.module.intake.service.ArchiveIntakePackageParser.ArchiveIntakeManifest;
import github.luckygc.am.module.intake.service.ArchiveIntakePackageParser.ArchiveIntakeManifestItem;
import github.luckygc.am.module.intake.service.ArchiveIntakePackageParser.ArchiveIntakeValidationResult;
import github.luckygc.am.module.intake.service.ArchiveIntakePackageParser.PackageStatistics;
import github.luckygc.am.module.intake.service.ArchiveIntakePackageParser.ParsedFile;
import github.luckygc.am.module.intake.service.ArchiveIntakeZipExtractor.ExtractedEntry;
import github.luckygc.am.module.intake.service.ArchiveIntakeZipExtractor.ExtractedPackage;

/** 标准目录关联、数量校验及接收清单和四性检测结果组装。 */
final class ArchiveIntakeManifestAssembler {

    private static final String EXPLANATION_NAME = "说明文件.TXT";
    private static final String CATALOG_NAME = "目录文件.XML";
    private static final String OTHER_DIRECTORY = "其他";
    private static final Pattern METADATA_FILE_NAME = Pattern.compile("^电子档案.*元数据\\.XML$");

    private final ArchiveIntakeDocumentParser documentParser;

    ArchiveIntakeManifestAssembler(ArchiveIntakeDocumentParser documentParser) {
        this.documentParser = documentParser;
    }

    ArchiveIntakeManifest assemble(ExtractedPackage extracted, Path tempDirectory) {
        NormalizedTree tree = normalizeRoot(extracted.entries());
        ExtractedEntry explanation = requiredFile(tree.entries(), EXPLANATION_NAME);
        ExtractedEntry catalog = requiredFile(tree.entries(), CATALOG_NAME);
        requireStandardFileSize(explanation);
        requireStandardFileSize(catalog);

        String explanationText = documentParser.readText(explanation.path(), EXPLANATION_NAME);
        ExplanationData explanationData = documentParser.parseExplanation(explanationText);
        CatalogData catalogData = documentParser.parseCatalog(catalog.path());
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
                        extracted.totalBytes());

        PackageStatistics statistics =
                new PackageStatistics(
                        catalogData.items().size(),
                        packageTree.contentFileCount(),
                        extracted.totalBytes(),
                        extracted.zipEntryCount());
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
                    documentParser.parseXml(file.path(), file.logicalPath());
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

    private <T> T requireNonNull(@Nullable T value) {
        if (value == null) {
            throw new IllegalStateException("解析后的文件信息不完整");
        }
        return value;
    }

    private BadRequestException invalid(String message) {
        return new BadRequestException(message);
    }

    private record NormalizedTree(Map<String, ExtractedEntry> entries) {}

    private record ArchiveDirectory(
            String fondsCode,
            String categoryCode,
            List<ParsedFile> metadataFiles,
            List<ParsedFile> contentFiles) {}

    private record PackageTreeData(Map<String, ArchiveDirectory> archives, int contentFileCount) {}
}
