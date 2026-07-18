package github.luckygc.am.module.archive.item.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.List;

import jakarta.data.page.PageRequest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.TestingAuthenticationToken;

import github.luckygc.am.common.exception.BadRequestException;
import github.luckygc.am.common.security.AuthenticatedUser;
import github.luckygc.am.module.archive.item.service.ArchiveItemLineRowService;
import github.luckygc.am.module.archive.item.service.ArchiveItemLineRowService.ArchiveItemLineFieldDefinitionResponse;
import github.luckygc.am.module.archive.item.service.ArchiveItemLineRowService.ArchiveItemLineTableDefinitionResponse;
import github.luckygc.am.module.archive.item.service.ArchiveItemLineRowService.PatchArchiveItemLineRowRequest;
import github.luckygc.am.module.archive.metadata.ArchiveFieldType;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@DisplayName("档案明细行 HTTP 入口")
class ArchiveItemLineRowControllerTests {

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();
    private final ArchiveItemLineRowService service = mock(ArchiveItemLineRowService.class);
    private final ArchiveItemLineRowController controller =
            new ArchiveItemLineRowController(service);

    @Test
    @DisplayName("条目范围定义响应不泄露物理存储标识符")
    void listLineTablesShouldExposeReadOnlyDefinitionView() throws Exception {
        org.mockito.Mockito.when(service.listLineTables(3L, 8L))
                .thenReturn(
                        List.of(
                                new ArchiveItemLineTableDefinitionResponse(
                                        4L,
                                        "contract_party",
                                        "合同方",
                                        1,
                                        List.of(
                                                new ArchiveItemLineFieldDefinitionResponse(
                                                        5L,
                                                        "party_name",
                                                        "单位名称",
                                                        ArchiveFieldType.TEXT,
                                                        1)))));

        String json = JSON_MAPPER.writeValueAsString(controller.listLineTables(3L, auth()));

        assertThat(json).contains("contract_party", "party_name", "TEXT");
        assertThat(json).doesNotContain("physicalTableName", "columnName", "exactSearchable");
    }

    @Test
    @DisplayName("列表将完整路径和 PageRequest 交给服务")
    void listRowsShouldDelegateCompletePath() {
        PageRequest page = PageRequest.ofSize(20);

        controller.listRows(3L, 4L, page, auth());

        verify(service).listRows(3L, 4L, page, 8L);
    }

    @Test
    @DisplayName("PATCH 区分 values 和 lineOrder 缺失")
    void patchShouldPreserveMissingFields() throws Exception {
        controller.patchRow(3L, 4L, 9L, json("{}"), auth());

        ArgumentCaptor<PatchArchiveItemLineRowRequest> captor =
                ArgumentCaptor.forClass(PatchArchiveItemLineRowRequest.class);
        verify(service)
                .patchRow(
                        org.mockito.ArgumentMatchers.eq(3L),
                        org.mockito.ArgumentMatchers.eq(4L),
                        org.mockito.ArgumentMatchers.eq(9L),
                        captor.capture(),
                        org.mockito.ArgumentMatchers.eq(8L));
        assertThat(captor.getValue().lineOrderPresent()).isFalse();
        assertThat(captor.getValue().valuesPresent()).isFalse();
        assertThat(captor.getValue().values()).isEmpty();
    }

    @Test
    @DisplayName("PATCH values 内显式 null 原样保留")
    void patchShouldPreserveExplicitNullValue() throws Exception {
        controller.patchRow(3L, 4L, 9L, json("{\"values\":{\"remark\":null}}"), auth());

        ArgumentCaptor<PatchArchiveItemLineRowRequest> captor =
                ArgumentCaptor.forClass(PatchArchiveItemLineRowRequest.class);
        verify(service)
                .patchRow(
                        org.mockito.ArgumentMatchers.eq(3L),
                        org.mockito.ArgumentMatchers.eq(4L),
                        org.mockito.ArgumentMatchers.eq(9L),
                        captor.capture(),
                        org.mockito.ArgumentMatchers.eq(8L));
        assertThat(captor.getValue().valuesPresent()).isTrue();
        assertThat(captor.getValue().values()).containsEntry("remark", null);
    }

    @Test
    @DisplayName("PATCH 显式 null lineOrder 返回稳定字段错误")
    void patchShouldRejectNullLineOrder() throws Exception {
        assertThatThrownBy(
                        () -> controller.patchRow(3L, 4L, 9L, json("{\"lineOrder\":null}"), auth()))
                .isInstanceOfSatisfying(
                        BadRequestException.class,
                        exception ->
                                assertThat(exception.fieldViolations())
                                        .extracting("field")
                                        .containsExactly("lineOrder"));
    }

    @Test
    @DisplayName("PATCH 拒绝未知顶层字段和非法 values 类型")
    void patchShouldRejectUnknownAndInvalidFields() throws Exception {
        assertThatThrownBy(() -> controller.patchRow(3L, 4L, 9L, json("{\"unknown\":1}"), auth()))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> controller.patchRow(3L, 4L, 9L, json("{\"values\":[]}"), auth()))
                .isInstanceOfSatisfying(
                        BadRequestException.class,
                        exception ->
                                assertThat(exception.fieldViolations())
                                        .extracting("field")
                                        .containsExactly("values"));
    }

    @Test
    @DisplayName("PATCH 拒绝非对象请求体")
    void patchShouldRejectNonObjectBody() throws Exception {
        assertThatThrownBy(() -> controller.patchRow(3L, 4L, 9L, json("[]"), auth()))
                .isInstanceOf(BadRequestException.class);
    }

    private static JsonNode json(String value) throws Exception {
        return JSON_MAPPER.readTree(value);
    }

    private static TestingAuthenticationToken auth() {
        return new TestingAuthenticationToken(
                new AuthenticatedUser() {
                    @Override
                    public Long id() {
                        return 8L;
                    }

                    @Override
                    public String displayName() {
                        return "管理员";
                    }
                },
                null);
    }
}
