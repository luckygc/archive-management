package github.luckygc.am.infrastructure.runtime;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;

import github.luckygc.am.common.runtime.RuntimeJob;
import github.luckygc.am.common.runtime.RuntimeMessage;
import github.luckygc.am.common.runtime.RuntimeQueueAdapter;

class DatabaseRuntimeQueue implements RuntimeQueueAdapter {

    private final RuntimeMapper runtimeMapper;

    private final int maxAttempts;

    DatabaseRuntimeQueue(RuntimeMapper runtimeMapper) {
        this(runtimeMapper, 10);
    }

    DatabaseRuntimeQueue(RuntimeMapper runtimeMapper, int maxAttempts) {
        this.runtimeMapper = runtimeMapper;
        this.maxAttempts = maxAttempts;
    }

    @Override
    public String adapter() {
        return "database";
    }

    @Override
    public Long enqueue(String queueName, RuntimeMessage message, Instant availableAt) {
        String normalizedQueueName = requireText(queueName, "队列名称");
        return runtimeMapper.insertRuntimeJob(
                normalizedQueueName,
                message.specVersion(),
                message.id(),
                message.source(),
                message.type(),
                message.subject(),
                message.dataContentType(),
                message.dataJson(),
                toLocalDateTime(message.time()),
                toLocalDateTime(availableAt));
    }

    @Override
    public Optional<RuntimeJob> claim(String queueName, String workerId, Duration leaseDuration) {
        String normalizedQueueName = requireText(queueName, "队列名称");
        String normalizedWorkerId = requireText(workerId, "worker ID");
        long leaseSeconds = requireLeaseSeconds(leaseDuration);
        Map<String, Object> row =
                runtimeMapper.claimRuntimeJob(
                        normalizedQueueName, normalizedWorkerId, leaseSeconds);
        if (row == null || row.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(toJob(row));
    }

    @Override
    public boolean complete(Long jobId, String workerId) {
        return runtimeMapper.completeRuntimeJob(
                        requireJobId(jobId), requireText(workerId, "worker ID"))
                > 0;
    }

    @Override
    public boolean fail(Long jobId, String workerId, String errorMessage, Instant nextAvailableAt) {
        return runtimeMapper.failRuntimeJob(
                        requireJobId(jobId),
                        requireText(workerId, "worker ID"),
                        errorMessage,
                        toLocalDateTime(nextAvailableAt),
                        maxAttempts)
                > 0;
    }

    private String requireText(String value, String fieldName) {
        if (StringUtils.isBlank(value)) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }

    private Long requireJobId(Long jobId) {
        if (jobId == null || jobId < 1) {
            throw new IllegalArgumentException("任务 ID 必须大于 0");
        }
        return jobId;
    }

    private long requireLeaseSeconds(Duration leaseDuration) {
        if (leaseDuration == null || leaseDuration.toSeconds() < 1) {
            throw new IllegalArgumentException("队列租约必须至少 1 秒");
        }
        return leaseDuration.toSeconds();
    }

    private RuntimeJob toJob(Map<String, Object> row) {
        return new RuntimeJob(
                number(row, "id").longValue(),
                string(row, "queueName"),
                new RuntimeMessage(
                        string(row, "specVersion"),
                        string(row, "messageId"),
                        string(row, "messageSource"),
                        string(row, "messageType"),
                        string(row, "messageSubject"),
                        string(row, "dataContentType"),
                        string(row, "dataJson"),
                        toInstant(localDateTime(row, "messageTime"))),
                number(row, "attempts").intValue(),
                string(row, "lockedBy"),
                localDateTime(row, "lockedAt"),
                localDateTime(row, "leaseUntil"),
                localDateTime(row, "createdAt"));
    }

    private Number number(Map<String, Object> row, String key) {
        return (Number) value(row, key);
    }

    private String string(Map<String, Object> row, String key) {
        Object value = value(row, key);
        return value == null ? null : value.toString();
    }

    private LocalDateTime localDateTime(Map<String, Object> row, String key) {
        Object value = value(row, key);
        return value instanceof LocalDateTime localDateTime ? localDateTime : null;
    }

    private Object value(Map<String, Object> row, String key) {
        if (row.containsKey(key)) {
            return row.get(key);
        }
        return row.get(camelToSnake(key));
    }

    private String camelToSnake(String key) {
        StringBuilder result = new StringBuilder(key.length() + 4);
        for (int index = 0; index < key.length(); index++) {
            char ch = key.charAt(index);
            if (Character.isUpperCase(ch)) {
                result.append('_').append(Character.toLowerCase(ch));
            } else {
                result.append(ch);
            }
        }
        return result.toString();
    }

    private LocalDateTime toLocalDateTime(Instant instant) {
        if (instant == null) {
            return null;
        }
        return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
    }

    private Instant toInstant(LocalDateTime localDateTime) {
        if (localDateTime == null) {
            return null;
        }
        return localDateTime.atZone(ZoneId.systemDefault()).toInstant();
    }
}
