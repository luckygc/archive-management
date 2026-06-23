package github.luckygc.am.common.audit;

import java.time.LocalDateTime;

public interface UpdateAuditable {

    Long getUpdatedBy();

    void setUpdatedBy(Long updatedBy);

    LocalDateTime getUpdatedAt();

    void setUpdatedAt(LocalDateTime updatedAt);
}
