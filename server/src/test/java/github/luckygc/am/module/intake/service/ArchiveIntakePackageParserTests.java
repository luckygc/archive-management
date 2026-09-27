package github.luckygc.am.module.intake.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import github.luckygc.am.common.exception.BadRequestException;
import github.luckygc.am.module.intake.ArchiveIntakeValidationOutcome;

import tools.jackson.databind.json.JsonMapper;

@DisplayName("档案信息包解析")
class ArchiveIntakePackageParserTests {

    private final ArchiveIntakePackageParser parser =
            new ArchiveIntakePackageParser(JsonMapper.builder().build());

    @Test
    @DisplayName("解析唯一顶层目录中的标准件级信息包并清理临时文件")
    void parseStandardPackageAndCleanupTemporaryFiles() throws IOException {
        try (var parsed = parser.parse(validPackage("电子档案移交信息包/"))) {
            assertThat(parsed.formatVersion()).isEqualTo("DAT93_ITEM");
            assertThat(parsed.packageCode()).isEqualTo("PKG-001");
            assertThat(parsed.items()).hasSize(1);
            var item = parsed.items().getFirst();
            assertThat(item.fondsCode()).isEqualTo("F001");
            assertThat(item.categoryCode()).isEqualTo("WS");
            assertThat(item.archiveNo()).isEqualTo("WS-2026-001");
            assertThat(item.sequenceNumber()).isEqualTo("1");
            assertThat(item.responsible()).isEqualTo("测试单位");
            assertThat(item.title()).isEqualTo("测试题名");
            assertThat(item.date()).isEqualTo("20260101");
            assertThat(item.retentionPeriod()).isEqualTo("永久");
            assertThat(item.securityLevel()).isEqualTo("公开");
            assertThat(item.pageCount()).isEqualTo(2);
            assertThat(item.remarks()).isEmpty();
            assertThat(item.metadataFiles())
                    .singleElement()
                    .satisfies(
                            file -> {
                                assertThat(file.originalName()).isEqualTo("电子档案1元数据.XML");
                                assertThat(file.logicalPath())
                                        .isEqualTo("F001/WS/年度/WS-2026-001/电子档案1元数据.XML");
                                assertThat(file.sha256()).hasSize(64);
                                assertThat(file.size()).isPositive();
                                assertThat(file.temporaryPath()).exists();
                            });
            assertThat(item.contentFiles())
                    .singleElement()
                    .satisfies(
                            file -> {
                                assertThat(file.originalName()).isEqualTo("正文.pdf");
                                assertThat(file.temporaryPath()).exists();
                            });
            assertThat(parsed.statistics()).isNotNull();
            assertThat(parsed.statistics().itemCount()).isEqualTo(1);
            assertThat(parsed.statistics().contentFileCount()).isEqualTo(1);
            assertThat(parsed.validationResults())
                    .anySatisfy(
                            result -> {
                                assertThat(result.code()).isEqualTo("DAT70_STRUCTURE");
                                assertThat(result.outcome())
                                        .isEqualTo(ArchiveIntakeValidationOutcome.PASSED);
                            })
                    .anySatisfy(
                            result -> {
                                assertThat(result.code()).isEqualTo("DAT70_ANTIVIRUS");
                                assertThat(result.outcome())
                                        .isEqualTo(ArchiveIntakeValidationOutcome.MANUAL_REVIEW);
                            });

            var temporaryPath = item.contentFiles().getFirst().temporaryPath();
            parsed.close();
            assertThat(temporaryPath).doesNotExist();
        }
    }

    @Test
    @DisplayName("允许标准根内容直接放置并对缺失页数给出警告")
    void parseRootContentAndWarnMissingPageCount() throws IOException {
        byte[] bytes =
                zip(
                        entry("说明文件.TXT", explanation()),
                        entry("目录文件.XML", catalogWithoutPageCount()),
                        entry("F001/WS/WS-2026-001/电子档案元数据.XML", metadataXml()),
                        entry("F001/WS/WS-2026-001/正文.pdf", "content"));

        try (var parsed = parser.parse(bytes)) {
            assertThat(parsed.validationResults())
                    .anySatisfy(
                            result -> {
                                assertThat(result.code()).isEqualTo("DAT93_PAGE_COUNT");
                                assertThat(result.outcome())
                                        .isEqualTo(ArchiveIntakeValidationOutcome.WARNING);
                            });
        }
    }

    @Test
    @DisplayName("生产入口直接解析上传临时文件")
    void parseFromUploadPath() throws IOException {
        Path upload = Files.createTempFile("archive-intake-parser-test-", ".zip");
        try {
            Files.write(upload, validPackage(""));
            try (var parsed = parser.parse(upload)) {
                assertThat(parsed.items()).hasSize(1);
                assertThat(parsed.items().getFirst().contentFiles().getFirst().temporaryPath())
                        .exists();
            }
            assertThat(upload).exists();
        } finally {
            Files.deleteIfExists(upload);
        }
    }

    @Test
    @DisplayName("说明文件支持 GB18030 编码")
    void parseGb18030Explanation() throws IOException {
        byte[] bytes =
                zip(
                        binaryEntry(
                                "说明文件.TXT",
                                explanation()
                                        .getBytes(java.nio.charset.Charset.forName("GB18030"))),
                        entry("目录文件.XML", catalogXml()),
                        entry("F001/WS/WS-2026-001/电子档案元数据.XML", metadataXml()),
                        entry("F001/WS/WS-2026-001/正文.pdf", "content"));

        try (var parsed = parser.parse(bytes)) {
            assertThat(parsed.packageCode()).isEqualTo("PKG-001");
        }
    }

    @Test
    @DisplayName("拒绝父目录、反斜杠和点路径")
    void rejectUnsafePaths() throws IOException {
        for (String path :
                List.of("../说明文件.TXT", "root\\说明文件.TXT", "/说明文件.TXT", "root/./说明文件.TXT")) {
            byte[] bytes = zip(entry(path, explanation()));
            assertThatThrownBy(() -> parser.parse(bytes))
                    .as(path)
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("不安全路径");
        }
    }

    @Test
    @DisplayName("拒绝重复路径")
    void rejectDuplicatePath() throws IOException {
        byte[] bytes = zip(directoryEntry("说明文件.TXT/"), entry("说明文件.TXT", explanation()));

        assertThatThrownBy(() -> parser.parse(bytes))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("重复路径");
    }

    @Test
    @DisplayName("拒绝根目录额外文件")
    void rejectIllegalRootFile() throws IOException {
        List<Entry> entries = validEntries("");
        entries.add(entry("readme.md", "unexpected"));

        assertThatThrownBy(() -> parser.parse(zip(entries.toArray(Entry[]::new))))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("根目录包含非法文件");
    }

    @Test
    @DisplayName("拒绝缺少标准文件或全宗目录")
    void rejectMissingStandardStructure() throws IOException {
        assertThatThrownBy(
                        () ->
                                parser.parse(
                                        zip(
                                                entry("目录文件.XML", catalogXml()),
                                                entry("F001/WS/A/电子档案元数据.XML", metadataXml()),
                                                entry("F001/WS/A/a.pdf", "x"))))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("说明文件.TXT");

        assertThatThrownBy(
                        () ->
                                parser.parse(
                                        zip(
                                                entry("说明文件.TXT", explanation()),
                                                entry("目录文件.XML", catalogXml()),
                                                entry("其他/check.txt", "x"))))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("全宗目录");
    }

    @Test
    @DisplayName("拒绝目录 XML 的 DTD 和外部实体")
    void rejectCatalogXxe() throws IOException {
        String xml =
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE 文件目录 [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
                <文件目录><文件><顺序号>1</顺序号><档号>&xxe;</档号></文件></文件目录>
                """;
        List<Entry> entries = validEntries("");
        replace(entries, "目录文件.XML", entry("目录文件.XML", xml));

        assertThatThrownBy(() -> parser.parse(zip(entries.toArray(Entry[]::new))))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("禁止 DTD");
    }

    @Test
    @DisplayName("拒绝元数据 XML 的 DTD")
    void rejectMetadataDtd() throws IOException {
        String xml =
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE 元数据 [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
                <元数据>&xxe;</元数据>
                """;
        List<Entry> entries = validEntries("");
        replace(
                entries,
                "F001/WS/年度/WS-2026-001/电子档案1元数据.XML",
                entry("F001/WS/年度/WS-2026-001/电子档案1元数据.XML", xml));

        assertThatThrownBy(() -> parser.parse(zip(entries.toArray(Entry[]::new))))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("禁止 DTD");
    }

    @Test
    @DisplayName("拒绝目录 XML 非法编码和非法结构")
    void rejectInvalidCatalogEncodingAndStructure() throws IOException {
        List<Entry> invalidEncoding = validEntries("");
        replace(
                invalidEncoding,
                "目录文件.XML",
                entry("目录文件.XML", "<?xml version=\"1.0\" encoding=\"UTF-16\"?><文件目录/>"));
        assertThatThrownBy(() -> parser.parse(zip(invalidEncoding.toArray(Entry[]::new))))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("编码非法");

        List<Entry> invalidStructure = validEntries("");
        replace(
                invalidStructure,
                "目录文件.XML",
                entry("目录文件.XML", "<?xml version=\"1.0\" encoding=\"UTF-8\"?><错误根元素/>"));
        assertThatThrownBy(() -> parser.parse(zip(invalidStructure.toArray(Entry[]::new))))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("根元素");
    }

    @Test
    @DisplayName("拒绝说明文件中的档案数量与目录不一致")
    void rejectArchiveCountMismatch() throws IOException {
        List<Entry> entries = validEntries("");
        replace(entries, "说明文件.TXT", entry("说明文件.TXT", explanation().replace("档案数量：1", "档案数量：2")));

        assertThatThrownBy(() -> parser.parse(zip(entries.toArray(Entry[]::new))))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("条目数不一致");
    }

    @Test
    @DisplayName("拒绝重复档号、目录不一致和未归属档案目录")
    void rejectArchiveAssociationErrors() throws IOException {
        String duplicated =
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <文件目录>%s%s</文件目录>
                """
                        .formatted(
                                catalogItem("1", "WS-2026-001", true),
                                catalogItem("2", "WS-2026-001", true));
        List<Entry> duplicateEntries = validEntries("");
        replace(duplicateEntries, "目录文件.XML", entry("目录文件.XML", duplicated));
        assertThatThrownBy(() -> parser.parse(zip(duplicateEntries.toArray(Entry[]::new))))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("重复档号");

        List<Entry> mismatch = validEntries("");
        replace(
                mismatch,
                "F001/WS/年度/WS-2026-001/电子档案1元数据.XML",
                entry("F001/WS/年度/OTHER/电子档案1元数据.XML", metadataXml()));
        replace(
                mismatch,
                "F001/WS/年度/WS-2026-001/正文.pdf",
                entry("F001/WS/年度/OTHER/正文.pdf", "content"));
        assertThatThrownBy(() -> parser.parse(zip(mismatch.toArray(Entry[]::new))))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("未被目录文件");

        List<Entry> unassigned = validEntries("");
        unassigned.add(entry("F001/WS/orphan.pdf", "content"));
        assertThatThrownBy(() -> parser.parse(zip(unassigned.toArray(Entry[]::new))))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("未归属");
    }

    @Test
    @DisplayName("拒绝档案目录缺少元数据或内容文件")
    void rejectMissingArchiveFiles() throws IOException {
        List<Entry> noMetadata = validEntries("");
        noMetadata.removeIf(entry -> entry.name().endsWith("元数据.XML"));
        assertThatThrownBy(() -> parser.parse(zip(noMetadata.toArray(Entry[]::new))))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("缺少电子档案元数据");

        List<Entry> noContent = validEntries("");
        noContent.removeIf(entry -> entry.name().endsWith("正文.pdf"));
        assertThatThrownBy(() -> parser.parse(zip(noContent.toArray(Entry[]::new))))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("缺少内容文件");
    }

    @Test
    @DisplayName("拒绝超过 100 个目录条目")
    void rejectTooManyCatalogItems() throws IOException {
        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?><文件目录>");
        for (int index = 1; index <= 101; index++) {
            xml.append(catalogItem(String.valueOf(index), "A-" + index, true));
        }
        xml.append("</文件目录>");

        assertThatThrownBy(
                        () ->
                                parser.parse(
                                        zip(
                                                entry("说明文件.TXT", explanation()),
                                                entry("目录文件.XML", xml.toString()),
                                                entry("F001/WS/A-1/电子档案元数据.XML", metadataXml()),
                                                entry("F001/WS/A-1/a.pdf", "x"))))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("最多包含 100");
    }

    @Test
    @DisplayName("拒绝超过 500 个内容文件")
    void rejectTooManyContentFiles() throws IOException {
        List<Entry> entries = validEntries("");
        entries.removeIf(entry -> entry.name().endsWith("正文.pdf"));
        for (int index = 0; index <= 500; index++) {
            entries.add(entry("F001/WS/年度/WS-2026-001/content-" + index + ".bin", "x"));
        }

        assertThatThrownBy(() -> parser.parse(zip(entries.toArray(Entry[]::new))))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("不能超过 500");
    }

    @Test
    @DisplayName("拒绝超过 2 MiB 的说明文件和超过 1000 个 ZIP 条目")
    void rejectStandardFileAndZipEntryLimits() throws IOException {
        assertThatThrownBy(
                        () ->
                                parser.parse(
                                        zip(
                                                entry(
                                                        "说明文件.TXT",
                                                        "x"
                                                                .repeat(
                                                                        ArchiveIntakePackageParser
                                                                                        .MAX_STANDARD_FILE_BYTES
                                                                                + 1)),
                                                entry("目录文件.XML", catalogXml()),
                                                entry(
                                                        "F001/WS/WS-2026-001/电子档案元数据.XML",
                                                        metadataXml()),
                                                entry("F001/WS/WS-2026-001/content.bin", "x"))))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("2 MiB");

        List<Entry> tooManyEntries = new ArrayList<>();
        for (int index = 0; index <= ArchiveIntakePackageParser.MAX_ZIP_ENTRIES; index++) {
            tooManyEntries.add(directoryEntry("directory-" + index + "/"));
        }
        assertThatThrownBy(() -> parser.parse(zip(tooManyEntries.toArray(Entry[]::new))))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("1000");
    }

    @Test
    @DisplayName("拒绝超过 50 MiB 的压缩包和单个解压文件")
    void rejectCompressedAndSingleFileLimits() throws IOException {
        Path oversizedPackage = Files.createTempFile("archive-intake-oversized-", ".zip");
        try (RandomAccessFile file = new RandomAccessFile(oversizedPackage.toFile(), "rw")) {
            file.setLength((long) ArchiveIntakePackageParser.MAX_COMPRESSED_BYTES + 1);
        }
        try {
            assertThatThrownBy(() -> parser.parse(oversizedPackage))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("50 MiB");
        } finally {
            Files.deleteIfExists(oversizedPackage);
        }

        byte[] oversizedEntry =
                zipWithRepeatedContent((long) ArchiveIntakePackageParser.MAX_SINGLE_FILE_BYTES + 1);
        assertThatThrownBy(() -> parser.parse(oversizedEntry))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("单个文件")
                .hasMessageContaining("50 MiB");
    }

    @Test
    @DisplayName("拒绝加密标志和不支持的压缩算法")
    void rejectEncryptionAndUnsupportedCompression() throws IOException {
        byte[] encrypted = validPackage("");
        int encryptedCentral = findSignature(encrypted, 0x02014b50);
        encrypted[encryptedCentral + 8] |= 0x01;
        assertThatThrownBy(() -> parser.parse(encrypted))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("加密");

        byte[] unsupported = validPackage("");
        int unsupportedCentral = findSignature(unsupported, 0x02014b50);
        unsupported[unsupportedCentral + 10] = 99;
        unsupported[unsupportedCentral + 11] = 0;
        assertThatThrownBy(() -> parser.parse(unsupported))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("不支持的压缩算法");
    }

    private byte[] validPackage(String prefix) throws IOException {
        return zip(validEntries(prefix).toArray(Entry[]::new));
    }

    private List<Entry> validEntries(String prefix) {
        return new ArrayList<>(
                List.of(
                        entry(prefix + "说明文件.TXT", explanation()),
                        entry(prefix + "目录文件.XML", catalogXml()),
                        entry(prefix + "F001/WS/年度/WS-2026-001/电子档案1元数据.XML", metadataXml()),
                        entry(prefix + "F001/WS/年度/WS-2026-001/正文.pdf", "content")));
    }

    private String explanation() {
        return """
                移交单位：测试单位
                内容描述：测试档案
                起止档号：WS-2026-001
                档案数量：1
                软硬件环境：通用
                载体编号：PKG-001
                载体数量：1
                制作单位：测试单位
                检查单位：测试单位
                """;
    }

    private String catalogXml() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <文件目录>%s</文件目录>
                """
                .formatted(catalogItem("1", "WS-2026-001", true));
    }

    private String catalogWithoutPageCount() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <文件目录>%s</文件目录>
                """
                .formatted(catalogItem("1", "WS-2026-001", false));
    }

    private String catalogItem(String sequence, String archiveNo, boolean includePageCount) {
        return """
                <文件>
                  <顺序号>%s</顺序号>
                  <档号>%s</档号>
                  <责任者>测试单位</责任者>
                  <题名>测试题名</题名>
                  <日期>20260101</日期>
                  <保管期限>永久</保管期限>
                  <密级>公开</密级>
                  %s
                  <备注></备注>
                </文件>
                """
                .formatted(sequence, archiveNo, includePageCount ? "<页数>2</页数>" : "");
    }

    private String metadataXml() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <电子档案元数据><档号>WS-2026-001</档号></电子档案元数据>
                """;
    }

    private Entry entry(String name, String content) {
        return binaryEntry(name, content.getBytes(StandardCharsets.UTF_8));
    }

    private Entry binaryEntry(String name, byte[] content) {
        return new Entry(name, content);
    }

    private Entry directoryEntry(String name) {
        return new Entry(name, new byte[0]);
    }

    private byte[] zip(Entry... entries) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            for (Entry entry : entries) {
                zip.putNextEntry(new ZipEntry(entry.name()));
                zip.write(entry.content());
                zip.closeEntry();
            }
        }
        return output.toByteArray();
    }

    private byte[] zipWithRepeatedContent(long contentSize) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            for (Entry entry :
                    List.of(
                            entry("说明文件.TXT", explanation()),
                            entry("目录文件.XML", catalogXml()),
                            entry("F001/WS/WS-2026-001/电子档案元数据.XML", metadataXml()))) {
                zip.putNextEntry(new ZipEntry(entry.name()));
                zip.write(entry.content());
                zip.closeEntry();
            }
            zip.putNextEntry(new ZipEntry("F001/WS/WS-2026-001/content.bin"));
            byte[] buffer = new byte[8192];
            long remaining = contentSize;
            while (remaining > 0) {
                int written = Math.toIntExact(Math.min(buffer.length, remaining));
                zip.write(buffer, 0, written);
                remaining -= written;
            }
            zip.closeEntry();
        }
        return output.toByteArray();
    }

    private void replace(List<Entry> entries, String oldName, Entry replacement) {
        entries.removeIf(entry -> oldName.equals(entry.name()));
        entries.add(replacement);
    }

    private int findSignature(byte[] bytes, int signature) {
        for (int index = 0; index <= bytes.length - 4; index++) {
            int candidate =
                    Byte.toUnsignedInt(bytes[index])
                            | (Byte.toUnsignedInt(bytes[index + 1]) << 8)
                            | (Byte.toUnsignedInt(bytes[index + 2]) << 16)
                            | (Byte.toUnsignedInt(bytes[index + 3]) << 24);
            if (candidate == signature) {
                return index;
            }
        }
        throw new IllegalArgumentException("ZIP signature not found");
    }

    private record Entry(String name, byte[] content) {}
}
