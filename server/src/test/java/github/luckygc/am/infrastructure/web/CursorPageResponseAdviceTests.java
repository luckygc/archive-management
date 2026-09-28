package github.luckygc.am.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.mock.web.MockHttpServletRequest;

import github.luckygc.am.common.api.CursorPageResponse;
import github.luckygc.am.common.api.CursorPageTokenCodec;
import github.luckygc.am.common.api.CursorPageTokenContext;
import github.luckygc.am.module.archive.item.service.ArchiveItemSearchService.ArchiveItemListDto;

import tools.jackson.databind.json.JsonMapper;

@DisplayName("cursor 分页响应包装")
class CursorPageResponseAdviceTests {

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    private final CursorPageResponseAdvice advice = new CursorPageResponseAdvice();

    @Test
    @DisplayName("分页链接保留筛选参数并包含可校验的游标和 limit")
    void shouldReturnNavigablePageLinks() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/items");
        request.setQueryString("status=ACTIVE&keyword=%E5%90%88%E5%90%8C&limit=20");
        CursorPageTokenContext context = new CursorPageTokenContext("digest");
        CursorPageTokenValidationInterceptor.setContext(request, context);
        CursorPageResponse<String> page =
                CursorPageResponse.withCursorValues(
                        List.of("A", "B"), 20, List.of(1L), null, List.of(2L), null, null);

        Object body =
                advice.beforeBodyWrite(
                        page,
                        returnType(),
                        MediaType.APPLICATION_JSON,
                        JacksonJsonHttpMessageConverter.class,
                        new ServletServerHttpRequest(request),
                        null);

        assertThat(body).isInstanceOf(CursorPageResponse.class);
        @SuppressWarnings("unchecked")
        CursorPageResponse<String> response = (CursorPageResponse<String>) body;
        assertThat(response.items()).containsExactly("A", "B");
        assertThat(response.prev()).isNull();
        assertThat(response.next())
                .startsWith("/items?")
                .contains("status=ACTIVE", "keyword=%E5%90%88%E5%90%8C", "limit=20", "cursor=");
        assertThat(CursorPageTokenCodec.decode(cursorFrom(response.self())).context())
                .isEqualTo(context);
        assertThat(CursorPageTokenCodec.decode(cursorFrom(response.self())).limit()).isEqualTo(20);
        assertThat(CursorPageTokenCodec.decode(cursorFrom(response.next())).values())
                .isEqualTo(List.of(2L));
    }

    @Test
    @DisplayName("内部游标状态字段不序列化")
    void cursorStateShouldNotBeSerialized() throws Exception {
        CursorPageResponse<String> page =
                CursorPageResponse.withCursorValues(
                        List.of("A"), 20, List.of(1L), null, List.of(2L), null, 9L);

        String json = JSON_MAPPER.writeValueAsString(page);

        assertThat(json).contains("\"items\"");
        assertThat(json).contains("\"total\"");
        assertThat(json)
                .doesNotContain("limit")
                .doesNotContain("selfValues")
                .doesNotContain("prevValues")
                .doesNotContain("nextValues")
                .doesNotContain("firstValues");
    }

    @Test
    @DisplayName("档案搜索分页链接转换后保留分类与字段元数据")
    void shouldPreserveArchiveSearchMetadata() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/items");
        CursorPageTokenContext context = new CursorPageTokenContext("custom-digest");
        CursorPageTokenValidationInterceptor.setContext(request, context);
        ArchiveItemListDto page =
                new ArchiveItemListDto(
                        null,
                        List.of(),
                        CursorPageResponse.withCursorValues(
                                List.of(), 20, List.of(1L), null, List.of(2L), null, null));

        Object body =
                advice.beforeBodyWrite(
                        page,
                        archiveSearchReturnType(),
                        MediaType.APPLICATION_JSON,
                        JacksonJsonHttpMessageConverter.class,
                        new ServletServerHttpRequest(request),
                        null);

        assertThat(body).isInstanceOf(ArchiveItemListDto.class);
        ArchiveItemListDto response = (ArchiveItemListDto) body;
        assertThat(response.fields()).isEmpty();
        assertThat(response.category()).isNull();
        assertThat(CursorPageTokenCodec.decode(cursorFrom(response.self())).context())
                .isEqualTo(context);
        assertThat(CursorPageTokenCodec.decode(cursorFrom(response.next())).values())
                .isEqualTo(List.of(2L));
    }

    private static String cursorFrom(String link) {
        String query = link.substring(link.indexOf('?') + 1);
        return org.springframework.web.util.UriComponentsBuilder.fromUriString("/items?" + query)
                .build()
                .getQueryParams()
                .getFirst("cursor");
    }

    private static MethodParameter returnType() throws NoSuchMethodException {
        Method method = TestController.class.getDeclaredMethod("list", HttpServletRequest.class);
        return new MethodParameter(method, -1);
    }

    private static MethodParameter archiveSearchReturnType() throws NoSuchMethodException {
        Method method =
                TestController.class.getDeclaredMethod("archiveSearch", HttpServletRequest.class);
        return new MethodParameter(method, -1);
    }

    static class TestController {
        CursorPageResponse<String> list(HttpServletRequest request) {
            return CursorPageResponse.withCursorValues(List.of(), 20, null, null, null, null, null);
        }

        ArchiveItemListDto archiveSearch(HttpServletRequest request) {
            return new ArchiveItemListDto(
                    null,
                    List.of(),
                    CursorPageResponse.withCursorValues(
                            List.of(), 20, null, null, null, null, null));
        }
    }
}
