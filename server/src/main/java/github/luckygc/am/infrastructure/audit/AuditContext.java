package github.luckygc.am.infrastructure.audit;

import java.time.LocalDateTime;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

public record AuditContext(LocalDateTime now, @Nullable Long userId) {

    public AuditContext {
        Objects.requireNonNull(now, "now");
    }
}
