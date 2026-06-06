package github.luckygc.am.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.ServletException;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class TraceIdFilterTests {

    private final TraceIdFilter filter = new TraceIdFilter();

    @Test
    void useRequestTraceIdAndExposeResponseHeader() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(TraceIdFilter.TRACE_ID_HEADER, "trace-20260606");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) ->
                assertThat(MDC.get(TraceIdFilter.TRACE_ID)).isEqualTo("trace-20260606"));

        assertThat(response.getHeader(TraceIdFilter.TRACE_ID_HEADER)).isEqualTo("trace-20260606");
        assertThat(MDC.get(TraceIdFilter.TRACE_ID)).isNull();
    }

    @Test
    void generateTraceIdWhenRequestHeaderIsInvalid() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(TraceIdFilter.TRACE_ID_HEADER, "bad trace id");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) ->
                assertThat(MDC.get(TraceIdFilter.TRACE_ID)).isNotEqualTo("bad trace id"));

        assertThat(response.getHeader(TraceIdFilter.TRACE_ID_HEADER))
                .isNotBlank()
                .isNotEqualTo("bad trace id");
        assertThat(MDC.get(TraceIdFilter.TRACE_ID)).isNull();
    }
}
