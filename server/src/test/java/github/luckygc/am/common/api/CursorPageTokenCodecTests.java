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
    @DisplayName("token 不绑定查询条件")
    void tokenShouldNotBindQueryCondition() {
        String token = CursorPageTokenCodec.encode("next", List.of(99L));

        PageRequest pageRequest = CursorPageTokenCodec.pageRequest(20, token, false);

        assertThat(pageRequest.cursor().orElseThrow().elements()).isEqualTo(List.of(99L));
    }

    @Test
    @DisplayName("token 被篡改时拒绝")
    void tamperedTokenShouldBeRejected() {
        String token = CursorPageTokenCodec.encode("next", List.of(99L));

        assertThatThrownBy(() -> CursorPageTokenCodec.pageRequest(20, token + "x", false))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("分页 cursor 无效");
    }
}
