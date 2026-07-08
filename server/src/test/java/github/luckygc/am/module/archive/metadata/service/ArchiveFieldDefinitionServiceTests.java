package github.luckygc.am.module.archive.metadata.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import github.luckygc.am.module.archive.ArchiveLevel;
import github.luckygc.am.module.archive.metadata.ArchiveField;
import github.luckygc.am.module.archive.metadata.ArchiveFieldControl;
import github.luckygc.am.module.archive.metadata.ArchiveFieldScope;
import github.luckygc.am.module.archive.metadata.ArchiveFieldType;
import github.luckygc.am.module.archive.metadata.ArchiveManagementMode;
import github.luckygc.am.module.archive.metadata.ArchiveTableStatus;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataService.ArchiveCategoryDto;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataService.ArchiveFieldDto;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataService.ArchiveFieldRequest;

@DisplayName("档案字段定义规则")
class ArchiveFieldDefinitionServiceTests {

    private final ArchiveFieldDefinitionService service = new ArchiveFieldDefinitionService();

    @Test
    @DisplayName("文本字段使用默认长度和输入框控件")
    void validateShouldApplyTextDefaults() {
        ArchiveFieldDefinitionService.ArchiveFieldValues values =
                service.validate(
                        new ArchiveFieldRequest(
                                null,
                                null,
                                " title ",
                                " 标题 ",
                                ArchiveFieldType.TEXT,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null));

        assertThat(values.archiveLevel()).isEqualTo(ArchiveLevel.ITEM);
        assertThat(values.fieldScope()).isEqualTo(ArchiveFieldScope.METADATA);
        assertThat(values.fieldCode()).isEqualTo("title");
        assertThat(values.fieldName()).isEqualTo("标题");
        assertThat(values.columnName()).isEqualTo("f_title");
        assertThat(values.textLength()).isEqualTo(500);
        assertThat(values.editControl()).isEqualTo(ArchiveFieldControl.INPUT);
        assertThat(values.listVisible()).isTrue();
        assertThat(values.detailColSpan()).isEqualTo(1);
        assertThat(values.enabled()).isTrue();
    }

    @Test
    @DisplayName("拒绝档案记录固定字段编码")
    void validateShouldRejectReservedRecordFieldCode() {
        assertThatThrownBy(
                        () ->
                                service.validate(
                                        new ArchiveFieldRequest(
                                                ArchiveLevel.ITEM,
                                                ArchiveFieldScope.METADATA,
                                                "archive_no",
                                                "档号",
                                                ArchiveFieldType.TEXT,
                                                100,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                true,
                                                0)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("字段编码属于档案记录固定字段");
    }

    @Test
    @DisplayName("拒绝字段类型和编辑控件不匹配")
    void validateShouldRejectIncompatibleEditControl() {
        assertThatThrownBy(
                        () ->
                                service.validate(
                                        new ArchiveFieldRequest(
                                                ArchiveLevel.ITEM,
                                                ArchiveFieldScope.METADATA,
                                                "amount",
                                                "金额",
                                                ArchiveFieldType.DECIMAL,
                                                null,
                                                18,
                                                2,
                                                ArchiveFieldControl.TEXTAREA,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                true,
                                                0)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("编辑控件与字段类型不匹配");
    }

    @Test
    @DisplayName("字段值可写入实体并生成列名")
    void applyValuesShouldPopulateEntity() {
        ArchiveFieldDefinitionService.ArchiveFieldValues values =
                service.validate(
                        new ArchiveFieldRequest(
                                ArchiveLevel.VOLUME,
                                ArchiveFieldScope.PHYSICAL,
                                "box_no",
                                "盒号",
                                ArchiveFieldType.TEXT,
                                80,
                                null,
                                null,
                                ArchiveFieldControl.INPUT,
                                true,
                                120,
                                5,
                                true,
                                1,
                                6,
                                true,
                                1,
                                7,
                                true,
                                true,
                                true,
                                8));
        ArchiveField field = new ArchiveField();

        service.applyValues(field, 12L, values);

        assertThat(field.getCategoryId()).isEqualTo(12L);
        assertThat(field.getArchiveLevel()).isEqualTo(ArchiveLevel.VOLUME);
        assertThat(field.getFieldScope()).isEqualTo(ArchiveFieldScope.PHYSICAL);
        assertThat(field.getFieldCode()).isEqualTo("box_no");
        assertThat(field.getColumnName()).isEqualTo("f_box_no");
        assertThat(field.isExactSearchable()).isTrue();
        assertThat(field.isDataScopeFilterable()).isTrue();
    }

    @Test
    @DisplayName("SQL 类型按字段配置生成")
    void sqlTypeShouldUseFieldConfiguration() {
        assertThat(service.sqlType(field(ArchiveFieldType.TEXT, 120, null, null)))
                .isEqualTo("varchar(120)");
        assertThat(service.sqlType(field(ArchiveFieldType.INTEGER, null, null, null)))
                .isEqualTo("integer");
        assertThat(service.sqlType(field(ArchiveFieldType.DECIMAL, null, 12, 4)))
                .isEqualTo("numeric(12,4)");
        assertThat(service.sqlType(field(ArchiveFieldType.DATE, null, null, null)))
                .isEqualTo("date");
        assertThat(service.sqlType(field(ArchiveFieldType.DATETIME, null, null, null)))
                .isEqualTo("timestamp");
    }

    @Test
    @DisplayName("案卷字段要求分类启用案卷管理")
    void ensureArchiveLevelAllowedShouldRejectVolumeForItemOnlyCategory() {
        assertThatThrownBy(
                        () ->
                                service.ensureArchiveLevelAllowed(
                                        category(ArchiveManagementMode.ITEM_ONLY),
                                        ArchiveLevel.VOLUME))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("该分类未启用案卷管理");
    }

    private static ArchiveCategoryDto category(ArchiveManagementMode mode) {
        return new ArchiveCategoryDto(
                1L,
                2L,
                null,
                "contract",
                "合同档案",
                mode,
                null,
                null,
                null,
                null,
                ArchiveTableStatus.NOT_BUILT,
                null,
                true,
                0,
                java.time.LocalDateTime.now(),
                java.time.LocalDateTime.now());
    }

    private static ArchiveFieldDto field(
            ArchiveFieldType type,
            Integer textLength,
            Integer decimalPrecision,
            Integer decimalScale) {
        return new ArchiveFieldDto(
                1L,
                12L,
                ArchiveLevel.ITEM,
                ArchiveFieldScope.METADATA,
                "field_code",
                "字段",
                type,
                "f_field_code",
                textLength,
                decimalPrecision,
                decimalScale,
                ArchiveFieldControl.INPUT,
                true,
                null,
                0,
                true,
                1,
                0,
                true,
                1,
                0,
                false,
                false,
                true,
                0,
                null,
                null,
                null);
    }
}
