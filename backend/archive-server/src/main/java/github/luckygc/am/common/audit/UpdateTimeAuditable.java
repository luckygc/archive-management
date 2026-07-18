package github.luckygc.am.common.audit;

import java.time.LocalDateTime;

public interface UpdateTimeAuditable {

    LocalDateTime getUpdatedAt();

    void setUpdatedAt(LocalDateTime updatedAt);

    default String updatedAtPropertyName() {
        return "updatedAt";
    }
}
