package github.luckygc.am.common.runtime;

import java.time.LocalDateTime;

/** 从运行时队列认领出来的作业快照。 */
public record RuntimeJob(
        Long id,
        String queueName,
        RuntimeMessage message,
        int attempts,
        String lockedBy,
        LocalDateTime lockedAt,
        LocalDateTime leaseUntil,
        LocalDateTime createdAt) {}
