package github.luckygc.am.common.audit;

import java.time.LocalDateTime;

public interface CreationTimeAuditable {

    LocalDateTime getCreatedAt();

    void setCreatedAt(LocalDateTime createdAt);

    default String createdAtPropertyName() {
        return "createdAt";
    }
}
