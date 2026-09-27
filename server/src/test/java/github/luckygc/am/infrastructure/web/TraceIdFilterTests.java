package github.luckygc.am.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import jakarta.servlet.ServletException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@DisplayName("TraceId 过滤器")
class TraceIdFilterTests {

    private final TraceIdFilter filter = new TraceIdFilter();

    @Test
    @DisplayName("使用请求 TraceId 并写回响应头")
    void useRequestTraceIdAndExposeResponseHeader() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(TraceIdFilter.TRACE_ID_HEADER, "trace-20260606");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(
                request,
                response,
                (req, res) ->
                        assertThat(MDC.get(TraceIdFilter.TRACE_ID)).isEqualTo("trace-20260606"));

        assertThat(response.getHeader(TraceIdFilter.TRACE_ID_HEADER)).isEqualTo("trace-20260606");
        assertThat(MDC.get(TraceIdFilter.TRACE_ID)).isNull();
    }

    @Test
    @DisplayName("请求 TraceId 非法时生成新的 TraceId")
    void generateTraceIdWhenRequestHeaderIsInvalid() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(TraceIdFilter.TRACE_ID_HEADER, "bad trace id");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(
                request,
                response,
                (req, res) ->
                        assertThat(MDC.get(TraceIdFilter.TRACE_ID)).isNotEqualTo("bad trace id"));

        assertThat(response.getHeader(TraceIdFilter.TRACE_ID_HEADER))
                .isNotBlank()
                .isNotEqualTo("bad trace id");
        assertThat(MDC.get(TraceIdFilter.TRACE_ID)).isNull();
    }
}
