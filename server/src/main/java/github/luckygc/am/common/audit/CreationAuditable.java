package github.luckygc.am.common.audit;

import java.time.LocalDateTime;

public interface CreationAuditable {

    Long getCreatedBy();

    void setCreatedBy(Long createdBy);

    LocalDateTime getCreatedAt();

    void setCreatedAt(LocalDateTime createdAt);
}
