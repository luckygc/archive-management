package github.luckygc.am.common.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.NoSuchElementException;

import jakarta.data.page.PageRequest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("keyset cursor 分页记录")
class KeysetCursoredPageRecordTests {

    @Test
    @DisplayName("按当前页首尾 cursor 生成上一页和下一页请求")
    void shouldBuildCursorNavigationRequestsFromPageEdges() {
        PageRequest request = PageRequest.ofSize(2).withTotal();
        KeysetCursoredPageRecord<String> page =
                new KeysetCursoredPageRecord<>(
                        request,
                        List.of("A", "B"),
                        List.of(PageRequest.Cursor.forKey(10L), PageRequest.Cursor.forKey(9L)),
                        false,
                        true,
                        5L);

        assertThat(page.content()).containsExactly("A", "B");
        assertThat(page.hasContent()).isTrue();
        assertThat(page.numberOfElements()).isEqualTo(2);
        assertThat(page.hasTotals()).isTrue();
        assertThat(page.totalElements()).isEqualTo(5L);
        assertThat(page.totalPages()).isEqualTo(3L);
        assertThat(page.hasPrevious()).isFalse();
        assertThat(page.hasNext()).isTrue();

        PageRequest next = page.nextPageRequest();
        assertThat(next.mode()).isEqualTo(PageRequest.Mode.CURSOR_NEXT);
        assertThat(next.cursor().orElseThrow().elements()).isEqualTo(List.of(9L));
        assertThat(next.requestTotal()).isFalse();

        assertThatThrownBy(page::previousPageRequest).isInstanceOf(NoSuchElementException.class);
    }

    @Test
    @DisplayName("内容行和 cursor 数量必须一致")
    void shouldRejectMismatchedContentAndCursorSizes() {
        PageRequest request = PageRequest.ofSize(2);

        assertThatThrownBy(
                        () ->
                                new KeysetCursoredPageRecord<>(
                                        request,
                                        List.of("A", "B"),
                                        List.of(PageRequest.Cursor.forKey(10L)),
                                        false,
                                        false,
                                        null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cursor");
    }
}
