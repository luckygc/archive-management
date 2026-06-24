package github.luckygc.am.common.runtime;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/** 运行时队列能力，用于可恢复后台任务和异步作业派发。 */
public interface RuntimeQueue {

    /** 写入一条可在指定时间后被认领的消息。 */
    Long enqueue(String queueName, RuntimeMessage message, Instant availableAt);

    /** 认领一条到期且未被其他 worker 持有的作业。 */
    Optional<RuntimeJob> claim(String queueName, String workerId, Duration leaseDuration);

    /** 由当前持有者完成作业，返回 false 表示持有者不匹配或作业不存在。 */
    boolean complete(Long jobId, String workerId);

    /** 标记作业失败并设置下一次可认领时间。 */
    boolean fail(Long jobId, String workerId, String errorMessage, Instant nextAvailableAt);
}
