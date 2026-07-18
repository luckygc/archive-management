package github.luckygc.am.common.audit;

import org.jspecify.annotations.Nullable;

public interface UpdateAuditable extends UpdateTimeAuditable {

    @Nullable Long getUpdatedBy();

    void setUpdatedBy(@Nullable Long updatedBy);
}
