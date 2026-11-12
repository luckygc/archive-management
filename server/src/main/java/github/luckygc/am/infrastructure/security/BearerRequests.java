package github.luckygc.am.infrastructure.security;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpHeaders;

public final class BearerRequests {

    private BearerRequests() {}

    public static boolean hasBearer(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        return header != null && header.regionMatches(true, 0, "Bearer ", 0, 7);
    }
}
