package github.luckygc.am.infrastructure.security;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import github.luckygc.am.common.exception.BadRequestException;
import github.luckygc.am.infrastructure.security.config.ApiRequestSignatureProperties;
import github.luckygc.am.infrastructure.web.GlobalExceptionHandler;

import tools.jackson.databind.json.JsonMapper;

@Component
public class ApiRequestSignatureFilter extends OncePerRequestFilter {

    static final String TIMESTAMP_HEADER = "X-AM-Timestamp";
    static final String NONCE_HEADER = "X-AM-Nonce";
    static final String SIGNATURE_HEADER = "X-AM-Signature";
    static final String NONCE_CACHE_NAME = "archive.security.request-signature.nonce";

    private final ApiRequestSignatureProperties properties;
    private final ApiRequestSigner signer;
    private final JsonMapper jsonMapper;
    private final Clock clock;
    private final Cache nonceCache;

    public ApiRequestSignatureFilter(
            ApiRequestSignatureProperties properties,
            ApiRequestSigner signer,
            JsonMapper jsonMapper,
            Clock clock,
            CacheManager cacheManager) {
        this.properties = properties;
        this.signer = signer;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
        if (properties.isEnabled() && StringUtils.length(properties.getSecret()) < 32) {
            throw new IllegalStateException(
                    "archive.security.request-signature.secret 至少需要 32 个字符");
        }
        this.nonceCache = nonceCache(properties, cacheManager);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !properties.isEnabled()
                || !request.getRequestURI().startsWith("/api/")
                || HttpMethod.OPTIONS.matches(request.getMethod())
                || isLoginBootstrapRequest(request)
                || isFileLinkDownloadRequest(request);
    }

    private boolean isLoginBootstrapRequest(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return "/api/v1/login-sessions".equals(uri)
                || "/api/v1/cap-challenges".equals(uri)
                || "/api/v1/cap-tokens".equals(uri)
                || "/api/v1/cap-tokens:validate".equals(uri);
    }

    private boolean isFileLinkDownloadRequest(HttpServletRequest request) {
        if (!HttpMethod.GET.matches(request.getMethod())) {
            return false;
        }
        String uri = request.getRequestURI();
        return (uri.startsWith("/api/v1/file-links/")
                        || uri.startsWith("/api/v1/public-file-links/"))
                && uri.endsWith(":download");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        CachedBodyRequest cachedRequest = new CachedBodyRequest(request);
        try {
            verify(cachedRequest);
            filterChain.doFilter(cachedRequest, response);
        } catch (BadRequestException exception) {
            writeBadRequest(response, exception);
        }
    }

    private void verify(CachedBodyRequest request) {
        String timestamp = requiredHeader(request, TIMESTAMP_HEADER);
        String nonce = requiredHeader(request, NONCE_HEADER);
        String signature = requiredHeader(request, SIGNATURE_HEADER);
        Instant requestTime = parseTimestamp(timestamp);
        Duration clockSkew = properties.getClockSkew();
        Instant now = clock.instant();
        if (requestTime.isBefore(now.minus(clockSkew))
                || requestTime.isAfter(now.plus(clockSkew))) {
            throw badRequest(TIMESTAMP_HEADER, "请求签名时间戳超出允许偏移");
        }

        ApiRequestSigner.CanonicalRequest canonicalRequest =
                new ApiRequestSigner.CanonicalRequest(
                        request.getMethod(),
                        request.getRequestURI(),
                        request.getQueryString(),
                        timestamp,
                        nonce,
                        DigestUtils.sha256Hex(request.body()));
        signer.verify(canonicalRequest, signature, properties.getSecret());
        consumeNonce(nonce);
    }

    private Cache nonceCache(ApiRequestSignatureProperties properties, CacheManager cacheManager) {
        Cache cache = cacheManager.getCache(NONCE_CACHE_NAME);
        if (!properties.isEnabled()) {
            return cache == null
                    ? new org.springframework.cache.support.NoOpCache(NONCE_CACHE_NAME)
                    : cache;
        }
        if (cache == null || cache.getClass().getName().contains("NoOpCache")) {
            throw new IllegalStateException("请求签名启用时必须配置可写入的 Spring Cache");
        }
        return cache;
    }

    private void consumeNonce(String nonce) {
        Cache.ValueWrapper previous = nonceCache.putIfAbsent(nonce, clock.instant().toString());
        if (previous != null) {
            throw badRequest(NONCE_HEADER, "请求签名 nonce 已使用");
        }
    }

    private String requiredHeader(HttpServletRequest request, String name) {
        String value = request.getHeader(name);
        if (StringUtils.isBlank(value)) {
            throw badRequest(name, "请求签名头不能为空");
        }
        return value;
    }

    private Instant parseTimestamp(String timestamp) {
        try {
            return Instant.parse(timestamp);
        } catch (DateTimeParseException exception) {
            throw badRequest(TIMESTAMP_HEADER, "请求签名时间戳必须使用 ISO-8601 UTC 时间");
        }
    }

    private BadRequestException badRequest(String field, String description) {
        return new BadRequestException("请求签名不合法", field, description);
    }

    private void writeBadRequest(HttpServletResponse response, BadRequestException exception)
            throws IOException {
        response.setStatus(HttpStatus.BAD_REQUEST.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
        problem.setTitle("请求参数无效");
        problem.setProperty("code", "INVALID_ARGUMENT");
        problem.setProperty("reason", "FIELD_VIOLATION");
        problem.setProperty("fieldViolations", exception.fieldViolations());
        jsonMapper.writeValue(response.getWriter(), GlobalExceptionHandler.problemBody(problem));
    }

    private static final class CachedBodyRequest extends HttpServletRequestWrapper {

        private final byte[] body;

        private CachedBodyRequest(HttpServletRequest request) throws IOException {
            super(request);
            this.body = request.getInputStream().readAllBytes();
        }

        private byte[] body() {
            return body;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override
                public int read() {
                    return inputStream.read();
                }

                @Override
                public boolean isFinished() {
                    return inputStream.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener readListener) {
                    throw new UnsupportedOperationException("同步请求体不支持异步读取");
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            Charset charset =
                    StringUtils.isBlank(getCharacterEncoding())
                            ? StandardCharsets.UTF_8
                            : Charset.forName(getCharacterEncoding());
            return new BufferedReader(new InputStreamReader(getInputStream(), charset));
        }
    }
}
