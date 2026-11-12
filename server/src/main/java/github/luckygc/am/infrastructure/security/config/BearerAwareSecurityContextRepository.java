package github.luckygc.am.infrastructure.security.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpRequestResponseHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;

import github.luckygc.am.infrastructure.security.BearerRequests;

/** Bearer 请求不读取、也不写入 HTTP 会话。containsContext 对 Bearer 返回 true，避免会话管理过滤器补建会话。 */
public class BearerAwareSecurityContextRepository implements SecurityContextRepository {

    private final SecurityContextRepository delegate = new HttpSessionSecurityContextRepository();

    @Override
    public SecurityContext loadContext(HttpRequestResponseHolder requestResponseHolder) {
        if (hasBearer(requestResponseHolder.getRequest())) {
            return SecurityContextHolder.createEmptyContext();
        }
        return delegate.loadContext(requestResponseHolder);
    }

    @Override
    public void saveContext(
            SecurityContext context, HttpServletRequest request, HttpServletResponse response) {
        if (hasBearer(request)) {
            return;
        }
        delegate.saveContext(context, request, response);
    }

    @Override
    public boolean containsContext(HttpServletRequest request) {
        if (hasBearer(request)) {
            return true;
        }
        return delegate.containsContext(request);
    }

    private static boolean hasBearer(HttpServletRequest request) {
        return request != null && BearerRequests.hasBearer(request);
    }
}
