package github.luckygc.am.module.authentication;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import github.luckygc.am.app.ArchiveManagementApplication;
import github.luckygc.am.module.authentication.repository.AuthenticationTotpCredentialDataRepository;
import github.luckygc.am.module.authentication.repository.AuthenticationUserDataRepository;
import github.luckygc.am.module.authentication.service.TotpCodeService;
import github.luckygc.am.module.authentication.service.TotpCredentialService;
import github.luckygc.am.test.PostgreSqlContainerTest;

@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(
        classes = ArchiveManagementApplication.class,
        properties = {
            "spring.quartz.auto-startup=false",
            "spring.session.jdbc.cleanup-cron=-",
            "flowable.async-executor-activate=false",
            "flowable.check-process-definitions=false",
            "flowable.eventregistry.enabled=false",
            "archive.authentication.bootstrap-admin.enabled=true",
            "archive.authentication.bootstrap-admin.username=admin",
            "archive.authentication.bootstrap-admin.password=Admin123!",
            "archive.authentication.bootstrap-admin.display-name=系统管理员",
            "archive.authentication.totp.encryption-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
        })
@DisplayName("TOTP 时间步并发防重放")
class TotpCredentialReplayProtectionIntegrationTests extends PostgreSqlContainerTest {

    @Autowired private AuthenticationTotpCredentialDataRepository credentialRepository;
    @Autowired private AuthenticationUserDataRepository userRepository;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private TotpCredentialService credentialService;
    @Autowired private TotpCodeService codeService;

    @Autowired
    @Qualifier("totpTextEncryptor") private TextEncryptor textEncryptor;

    private AuthenticationUser user;

    @BeforeEach
    void resetCredential() {
        user = userRepository.findOptionalByUsername("admin");
        assertThat(user).isNotNull();
        credentialRepository.deleteByUserId(user.getId());
    }

    @Test
    @DisplayName("PostgreSQL 条件更新保证并发接受同一时间步时仅一个事务成功")
    void concurrentAdvanceOfSameStepShouldUpdateExactlyOneRow() throws Exception {
        AuthenticationTotpCredential credential = new AuthenticationTotpCredential();
        credential.setUserId(user.getId());
        credential.setEncryptedSecret("integration-test-ciphertext");
        credential.setLastAcceptedStep(100L);
        credentialRepository.insert(credential);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        LocalDateTime updatedAt = LocalDateTime.of(2026, 8, 9, 0, 0);
        TransactionTemplate transactions = new TransactionTemplate(transactionManager);
        try (var executor = Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<Integer> advance =
                    () -> {
                        ready.countDown();
                        assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                        return transactions.execute(
                                _ ->
                                        credentialRepository.advanceAcceptedStep(
                                                user.getId(), 101L, updatedAt));
                    };
            Future<Integer> first = executor.submit(advance);
            Future<Integer> second = executor.submit(advance);
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(0, 1);
        }

        AuthenticationTotpCredential persisted =
                credentialRepository.findById(user.getId()).orElseThrow();
        assertThat(persisted.getLastAcceptedStep()).isEqualTo(101L);
        assertThat(persisted.getVersion()).isEqualTo(1);
        assertThat(persisted.getUpdatedAt()).isEqualTo(updatedAt);
    }

    @Test
    @DisplayName("条件推进时间步后停用凭据不会因实体版本陈旧而失败")
    void disableCredentialShouldAdvanceAndDeleteInOneTransaction() {
        String secret = codeService.generateSecret();
        Instant now = Instant.now();
        AuthenticationTotpCredential credential = new AuthenticationTotpCredential();
        credential.setUserId(user.getId());
        credential.setEncryptedSecret(textEncryptor.encrypt(secret));
        credential.setLastAcceptedStep(now.getEpochSecond() / 30L - 1L);
        credentialRepository.insert(credential);

        credentialService.disableCredential(
                new TotpCredentialService.DisableTotpCredentialRequest(
                        "Admin123!", codeService.generateCode(secret, now)),
                user.getId(),
                user.getUsername(),
                new MockHttpServletRequest());

        assertThat(credentialRepository.findById(user.getId())).isEmpty();
    }
}
