package github.luckygc.am.infrastructure.security.config;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "archive.security.cors")
public class SecurityCorsProperties {

    private List<String> allowedOrigins = new ArrayList<>();

    private List<String> allowedOriginPatterns = new ArrayList<>();

    private List<String> allowedMethods = new ArrayList<>(List.of(
            "GET",
            "POST",
            "PUT",
            "PATCH",
            "DELETE",
            "OPTIONS"
    ));

    private List<String> allowedHeaders = new ArrayList<>(List.of(
            "Content-Type",
            "X-XSRF-TOKEN",
            "X-Trace-Id"
    ));

    private List<String> exposedHeaders = new ArrayList<>(List.of("X-Trace-Id"));

    private boolean allowCredentials = true;

    private long maxAge = 3600L;
}
