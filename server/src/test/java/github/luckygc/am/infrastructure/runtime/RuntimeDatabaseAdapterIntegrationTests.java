package github.luckygc.am.infrastructure.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.junit.jupiter.Testcontainers;

import github.luckygc.am.app.ArchiveManagementApplication;
import github.luckygc.am.common.runtime.RuntimeLockManager;
import github.luckygc.am.common.runtime.RuntimeMessage;
import github.luckygc.am.common.runtime.RuntimeQueue;
import github.luckygc.am.test.PostgreSqlContainerTest;

@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(
        classes = ArchiveManagementApplication.class,
        properties = {
            "spring.autoconfigure.exclude=org.springframework.ai.model.deepseek.autoconfigure.DeepSeekChatAutoConfiguration",
            "spring.quartz.auto-startup=false",
            "spring.session.jdbc.cleanup-cron=-",
            "archive.runtime.queue.max-attempts=2",
            "flowable.async-executor-activate=false",
            "flowable.check-process-definitions=false",
            "flowable.eventregistry.enabled=false"
        })
@DisplayName("数据库运行时 adapter 集成")
class RuntimeDatabaseAdapterIntegrationTests extends PostgreSqlContainerTest {

    @Autowired private RuntimeQueue runtimeQueue;

    @Autowired private RuntimeLockManager runtimeLockManager;

    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("数据库队列支持认领、完成和失败重试")
    void databaseRuntimeQueueClaimsCompletesAndRetriesJobs() {
        String queueName = "test-" + UUID.randomUUID();
        Long futureJobId =
                runtimeQueue.enqueue(
                        queueName,
                        message("future", "{\"name\":\"future\"}"),
                        Instant.now().plusSeconds(60));

        assertThat(futureJobId).isNotNull();
        assertThat(runtimeQueue.claim(queueName, "node-a", Duration.ofMinutes(5))).isEmpty();

        Long jobId =
                runtimeQueue.enqueue(
                        queueName,
                        message("first", "{\"name\":\"first\"}"),
                        Instant.now().minusSeconds(1));
        var claimed = runtimeQueue.claim(queueName, "node-a", Duration.ofMinutes(5)).orElseThrow();

        assertThat(claimed.id()).isEqualTo(jobId);
        assertThat(claimed.message().id()).isEqualTo("first");
        assertThat(claimed.message().source()).isEqualTo("/tests/runtime");
        assertThat(claimed.message().type()).isEqualTo("github.luckygc.am.test.runtime");
        assertThat(claimed.message().dataJson()).isEqualTo("{\"name\":\"first\"}");
        assertThat(claimed.lockedBy()).isEqualTo("node-a");
        assertThat(runtimeQueue.complete(claimed.id(), "node-b")).isFalse();
        assertThat(runtimeQueue.complete(claimed.id(), "node-a")).isTrue();
        assertThat(runtimeQueue.claim(queueName, "node-a", Duration.ofMinutes(5))).isEmpty();

        Long retryJobId =
                runtimeQueue.enqueue(
                        queueName,
                        message("retry", "{\"name\":\"retry\"}"),
                        Instant.now().minusSeconds(1));
        var retryClaim =
                runtimeQueue.claim(queueName, "node-a", Duration.ofMinutes(5)).orElseThrow();

        assertThat(retryClaim.id()).isEqualTo(retryJobId);
        assertThat(
                        runtimeQueue.fail(
                                retryClaim.id(),
                                "node-a",
                                "retry later",
                                Instant.now().minusSeconds(1)))
                .isTrue();

        var reclaimed =
                runtimeQueue.claim(queueName, "node-b", Duration.ofMinutes(5)).orElseThrow();

        assertThat(reclaimed.id()).isEqualTo(retryJobId);
        assertThat(reclaimed.lockedBy()).isEqualTo("node-b");
        assertThat(reclaimed.attempts()).isEqualTo(2);
        assertThat(runtimeQueue.complete(reclaimed.id(), "node-b")).isTrue();
    }

    @Test
    @DisplayName("数据库队列租约过期后原持有者不能完成或失败任务")
    void databaseRuntimeQueueRejectsCompletionAfterLeaseExpired() throws InterruptedException {
        String queueName = "test-" + UUID.randomUUID();
        Long jobId =
                runtimeQueue.enqueue(
                        queueName,
                        message("expired", "{\"name\":\"expired\"}"),
                        Instant.now().minusSeconds(1));
        var claimed = runtimeQueue.claim(queueName, "node-a", Duration.ofSeconds(1)).orElseThrow();

        Thread.sleep(1200L);

        assertThat(claimed.id()).isEqualTo(jobId);
        assertThat(runtimeQueue.complete(claimed.id(), "node-a")).isFalse();
        assertThat(
                        runtimeQueue.fail(
                                claimed.id(),
                                "node-a",
                                "lease expired",
                                Instant.now().minusSeconds(1)))
                .isFalse();

        var reclaimed =
                runtimeQueue.claim(queueName, "node-b", Duration.ofMinutes(5)).orElseThrow();

        assertThat(reclaimed.id()).isEqualTo(jobId);
        assertThat(reclaimed.lockedBy()).isEqualTo("node-b");
        assertThat(runtimeQueue.complete(reclaimed.id(), "node-b")).isTrue();
    }

    @Test
    @DisplayName("数据库队列达到最大尝试次数后进入死信")
    void databaseRuntimeQueueMovesJobToDeadLetterAfterMaxAttempts() {
        String queueName = "test-" + UUID.randomUUID();
        Long jobId =
                runtimeQueue.enqueue(
                        queueName,
                        message("dead-letter", "{\"name\":\"dead-letter\"}"),
                        Instant.now().minusSeconds(1));

        var first = runtimeQueue.claim(queueName, "node-a", Duration.ofMinutes(5)).orElseThrow();
        assertThat(
                        runtimeQueue.fail(
                                first.id(),
                                "node-a",
                                "first failure",
                                Instant.now().minusSeconds(1)))
                .isTrue();

        var second = runtimeQueue.claim(queueName, "node-b", Duration.ofMinutes(5)).orElseThrow();
        assertThat(second.id()).isEqualTo(jobId);
        assertThat(second.attempts()).isEqualTo(2);
        assertThat(
                        runtimeQueue.fail(
                                second.id(),
                                "node-b",
                                "second failure",
                                Instant.now().minusSeconds(1)))
                .isTrue();

        assertThat(runtimeQueue.claim(queueName, "node-c", Duration.ofMinutes(5))).isEmpty();
        assertThat(
                        jdbcTemplate.queryForObject(
                                "select status from am_runtime_job where id = ?",
                                String.class,
                                jobId))
                .isEqualTo("dead_letter");
        assertThat(
                        jdbcTemplate.queryForObject(
                                "select dead_letter_at is not null from am_runtime_job where id = ?",
                                Boolean.class,
                                jobId))
                .isTrue();
    }

    @Test
    @DisplayName("数据库锁拒绝其他持有者并在关闭后释放")
    void databaseRuntimeLockRejectsOtherOwnerAndCloseReleasesLease() {
        String lockName = "test-" + UUID.randomUUID();

        try (var ignored =
                runtimeLockManager
                        .tryLock(lockName, "node-a", Duration.ofMinutes(5))
                        .orElseThrow()) {
            assertThat(runtimeLockManager.tryLock(lockName, "node-b", Duration.ofMinutes(5)))
                    .isEmpty();
        }

        var second = runtimeLockManager.tryLock(lockName, "node-b", Duration.ofMinutes(5));

        assertThat(second).isPresent();
        second.orElseThrow().close();
    }

    @Test
    @DisplayName("数据库锁租约过期后允许其他持有者接管")
    void databaseRuntimeLockAllowsOtherOwnerAfterLeaseExpired() throws InterruptedException {
        String lockName = "test-" + UUID.randomUUID();

        var first = runtimeLockManager.tryLock(lockName, "node-a", Duration.ofSeconds(1));

        assertThat(first).isPresent();
        assertThat(runtimeLockManager.tryLock(lockName, "node-b", Duration.ofMinutes(5))).isEmpty();

        Thread.sleep(1200L);

        var second = runtimeLockManager.tryLock(lockName, "node-b", Duration.ofMinutes(5));

        assertThat(second).isPresent();
        second.orElseThrow().close();
    }

    private RuntimeMessage message(String id, String dataJson) {
        return new RuntimeMessage(
                id,
                "/tests/runtime",
                "github.luckygc.am.test.runtime",
                "runtime-test",
                dataJson,
                Instant.now());
    }
}
