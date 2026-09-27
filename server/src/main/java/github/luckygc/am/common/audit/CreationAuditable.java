package github.luckygc.am.common.audit;

import org.jspecify.annotations.Nullable;

public interface CreationAuditable extends CreationTimeAuditable {

    @Nullable Long getCreatedBy();

    void setCreatedBy(@Nullable Long createdBy);
}
