package github.luckygc.am.infrastructure.web;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import github.luckygc.am.common.exception.BadRequestException;

public class CachedBodyHttpServletRequestWrapper extends HttpServletRequestWrapper {

    static final int MAX_JSON_BODY_BYTES = 1024 * 1024;

    private final byte[] cachedBody;

    public CachedBodyHttpServletRequestWrapper(HttpServletRequest request) throws IOException {
        super(request);
        if (request.getContentLengthLong() > MAX_JSON_BODY_BYTES) {
            throw new BadRequestException("请求体过大", "body", "请求体不能超过 1 MiB");
        }
        this.cachedBody = readBounded(request.getInputStream());
    }

    public byte[] getCachedBody() {
        return cachedBody.clone();
    }

    @Override
    public ServletInputStream getInputStream() {
        ByteArrayInputStream input = new ByteArrayInputStream(cachedBody);
        return new ServletInputStream() {
            @Override
            public boolean isFinished() {
                return input.available() == 0;
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setReadListener(ReadListener readListener) {}

            @Override
            public int read() {
                return input.read();
            }
        };
    }

    @Override
    public BufferedReader getReader() {
        Charset charset =
                getCharacterEncoding() == null
                        ? StandardCharsets.UTF_8
                        : Charset.forName(getCharacterEncoding());
        return new BufferedReader(new InputStreamReader(getInputStream(), charset));
    }

    static byte[] readBounded(InputStream inputStream) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;
        int read;
        while ((read = inputStream.read(buffer)) != -1) {
            total += read;
            if (total > MAX_JSON_BODY_BYTES) {
                throw new BadRequestException("请求体过大", "body", "请求体不能超过 1 MiB");
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }
}
