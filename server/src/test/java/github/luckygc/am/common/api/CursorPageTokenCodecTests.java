package github.luckygc.am.common.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import jakarta.data.page.PageRequest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import github.luckygc.am.common.exception.BadRequestException;

@DisplayName("游标分页 token 编码")
class CursorPageTokenCodecTests {

    @Test
    @DisplayName("从 PageRequest 生成 token 后可还原为下一页请求")
    void pageRequestTokenShouldRoundTrip() {
        PageRequest nextRequest =
                PageRequest.ofSize(20)
                        .afterCursor(
                                PageRequest.Cursor.forKey(
                                        LocalDateTime.of(2026, 7, 1, 10, 30), 99L, "admin"));

        String token = CursorPageTokenCodec.encode(nextRequest);
        PageRequest decoded = CursorPageTokenCodec.pageRequest(20, token, true);

        assertThat(decoded.mode()).isEqualTo(PageRequest.Mode.CURSOR_NEXT);
        assertThat(decoded.requestTotal()).isFalse();
        assertThat(decoded.cursor()).isPresent();
        assertThat(decoded.cursor().orElseThrow().elements())
                .isEqualTo(List.of(LocalDateTime.of(2026, 7, 1, 10, 30), 99L, "admin"));
    }

    @Test
    @DisplayName("第一页请求总数，后续 token 页不请求总数")
    void firstPageCanRequestTotalButTokenPageDoesNot() {
        PageRequest first = CursorPageTokenCodec.pageRequest(50, null, true);
        String next =
                CursorPageTokenCodec.encode(
                        "next", List.of(LocalDate.of(2026, 7, 1), Integer.valueOf(9)));

        PageRequest nextPage = CursorPageTokenCodec.pageRequest(50, next, true);

        assertThat(first.requestTotal()).isTrue();
        assertThat(nextPage.requestTotal()).isFalse();
        assertThat(nextPage.cursor().orElseThrow().elements())
                .isEqualTo(List.of(LocalDate.of(2026, 7, 1), Integer.valueOf(9)));
    }

    @Test
    @DisplayName("token 绑定页大小和查询摘要")
    void tokenShouldBindLimitAndQueryDigest() {
        CursorPageTokenContext context = new CursorPageTokenContext("digest-a");
        String token = CursorPageTokenCodec.encode("next", List.of(99L), 20, context);

        PageRequest pageRequest = CursorPageTokenCodec.pageRequest(20, token, false, context);

        assertThat(pageRequest.cursor().orElseThrow().elements()).isEqualTo(List.of(99L));
        assertThatThrownBy(() -> CursorPageTokenCodec.pageRequest(50, token, false, context))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("分页 cursor 无效")
                .satisfies(
                        exception ->
                                assertThat(((BadRequestException) exception).fieldViolations())
                                        .singleElement()
                                        .satisfies(
                                                violation ->
                                                        assertThat(violation.message())
                                                                .isEqualTo(
                                                                        "分页大小已变化，请从第一页重新查询")));
        assertThatThrownBy(
                        () ->
                                CursorPageTokenCodec.pageRequest(
                                        20,
                                        token,
                                        false,
                                        new CursorPageTokenContext("digest-b")))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("分页 cursor 无效")
                .satisfies(
                        exception ->
                                assertThat(((BadRequestException) exception).fieldViolations())
                                        .singleElement()
                                        .satisfies(
                                                violation ->
                                                        assertThat(violation.message())
                                                                .isEqualTo(
                                                                        "查询条件已变化，请从第一页重新查询")));
    }

    @Test
    @DisplayName("token 被篡改时拒绝")
    void tamperedTokenShouldBeRejected() {
        String token = CursorPageTokenCodec.encode("next", List.of(99L));

        assertThatThrownBy(() -> CursorPageTokenCodec.pageRequest(20, token + "x", false))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("分页 cursor 无效");
    }

    @Test
    @DisplayName("配置不同 cursor 密钥后旧 token 失效")
    void tokenShouldDependOnConfiguredSecret() {
        CursorPageTokenCodec.configureSecret("0123456789abcdef0123456789abcdef");
        String token = CursorPageTokenCodec.encode("next", List.of(99L));
        CursorPageTokenCodec.configureSecret("abcdef0123456789abcdef0123456789");

        assertThatThrownBy(() -> CursorPageTokenCodec.pageRequest(20, token, false))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("分页 cursor 无效");

        CursorPageTokenCodec.configureSecret("0123456789abcdef0123456789abcdef");
        assertThat(CursorPageTokenCodec.pageRequest(20, token, false).cursor()).isPresent();
    }
}
