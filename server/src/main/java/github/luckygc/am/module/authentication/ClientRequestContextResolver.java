package github.luckygc.am.module.authentication;

import jakarta.servlet.http.HttpServletRequest;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import nl.basjes.parse.useragent.UserAgent;
import nl.basjes.parse.useragent.UserAgentAnalyzer;

@Component
public class ClientRequestContextResolver {

    private final UserAgentAnalyzer userAgentAnalyzer =
            UserAgentAnalyzer.newBuilder()
                    .withField(UserAgent.AGENT_NAME)
                    .withField(UserAgent.AGENT_VERSION)
                    .withField(UserAgent.OPERATING_SYSTEM_NAME)
                    .withField(UserAgent.OPERATING_SYSTEM_VERSION)
                    .withField(UserAgent.DEVICE_CLASS)
                    .withCache(1000)
                    .build();

    public ClientRequestContext resolve(HttpServletRequest request) {
        String userAgent = header(request, "User-Agent");
        return new ClientRequestContext(
                StringUtils.defaultString(request.getRemoteAddr()),
                header(request, "Host"),
                header(request, "Forwarded"),
                header(request, "X-Forwarded-For"),
                header(request, "X-Real-IP"),
                parseClient(userAgent));
    }

    private ClientInfo parseClient(String userAgent) {
        UserAgent parsed = userAgentAnalyzer.parse(userAgent);
        return new ClientInfo(
                userAgent,
                normalized(parsed.getValue(UserAgent.AGENT_NAME)),
                normalized(parsed.getValue(UserAgent.AGENT_VERSION)),
                normalized(parsed.getValue(UserAgent.OPERATING_SYSTEM_NAME)),
                normalized(parsed.getValue(UserAgent.OPERATING_SYSTEM_VERSION)),
                normalizedDeviceType(parsed.getValue(UserAgent.DEVICE_CLASS)));
    }

    private String header(HttpServletRequest request, String name) {
        return StringUtils.defaultString(request.getHeader(name));
    }

    private String normalized(String value) {
        if (StringUtils.isBlank(value)
                || UserAgent.UNKNOWN_VALUE.equals(value)
                || UserAgent.UNKNOWN_VERSION.equals(value)) {
            return "";
        }
        return value;
    }

    private String normalizedDeviceType(String value) {
        String normalized = normalized(value);
        if (StringUtils.equalsIgnoreCase(normalized, "Desktop")) {
            return "desktop";
        }
        if (StringUtils.equalsAnyIgnoreCase(normalized, "Phone", "Mobile")) {
            return "mobile";
        }
        if (StringUtils.equalsIgnoreCase(normalized, "Tablet")) {
            return "tablet";
        }
        return StringUtils.defaultIfBlank(normalized.toLowerCase(), "unknown");
    }
}
