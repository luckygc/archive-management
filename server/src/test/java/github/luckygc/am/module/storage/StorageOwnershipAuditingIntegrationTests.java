package github.luckygc.am.module.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Testcontainers;

import github.luckygc.am.app.ArchiveManagementApplication;
import github.luckygc.am.module.authentication.ArchiveUserDetails;
import github.luckygc.am.module.storage.repository.FileLinkDataRepository;
import github.luckygc.am.module.storage.repository.StorageObjectDataRepository;
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
            "flowable.eventregistry.enabled=false"
        })
@DisplayName("存储业务所有者与 Hibernate 通用审计边界")
class StorageOwnershipAuditingIntegrationTests extends PostgreSqlContainerTest {

    private static final LocalDateTime FORGED_CREATED_AT = LocalDateTime.of(2000, 1, 1, 0, 0);

    @Autowired private StorageObjectDataRepository storageObjectRepository;
    @Autowired private FileLinkDataRepository fileLinkRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @Transactional
    @DisplayName("安全主体不同于对象所有者时 Repository 保留显式所有者")
    void storageObjectKeepsExplicitOwnerWhenSecurityActorDiffers() {
        SecurityContextHolder.getContext()
                .setAuthentication(
                        UsernamePasswordAuthenticationToken.authenticated(
                                new ArchiveUserDetails(
                                        99L, "storage-auditor", "N/A", true, "存储审计主体", List.of()),
                                "N/A",
                                List.of()));
        StorageObject object = new StorageObject();
        object.setBucketName("audit-owner-bucket");
        object.setObjectKey("audit-owner/storage-object");
        object.setOriginalFilename("owner.txt");
        object.setFileSize(1L);
        object.setCreatedBy(9L);
        object.setCreatedAt(FORGED_CREATED_AT);

        StorageObject inserted = storageObjectRepository.insert(object);

        assertThat(
                        jdbcTemplate.queryForObject(
                                "select created_by from am_storage_object where id = ?",
                                Long.class,
                                inserted.getId()))
                .isEqualTo(9L);
        assertThat(
                        jdbcTemplate.queryForObject(
                                "select created_at from am_storage_object where id = ?",
                                LocalDateTime.class,
                                inserted.getId()))
                .isNotEqualTo(FORGED_CREATED_AT);
    }

    @Test
    @Transactional
    @DisplayName("无安全主体时 Repository 保留文件短链显式创建人")
    void fileLinkKeepsExplicitCreatorWithoutSecurityActor() {
        FileLink link = new FileLink();
        link.setCode("AUDIT-OWNER-LINK");
        link.setTargetType(FileLinkTargetType.STORAGE_OBJECT);
        link.setTargetId(10L);
        link.setExpiresAt(LocalDateTime.of(2026, 7, 16, 23, 0));
        link.setCreatedBy(8L);
        link.setCreatedAt(FORGED_CREATED_AT);

        FileLink inserted = fileLinkRepository.insert(link);

        assertThat(
                        jdbcTemplate.queryForObject(
                                "select created_by from am_file_link where id = ?",
                                Long.class,
                                inserted.getId()))
                .isEqualTo(8L);
        assertThat(
                        jdbcTemplate.queryForObject(
                                "select created_at from am_file_link where id = ?",
                                LocalDateTime.class,
                                inserted.getId()))
                .isNotEqualTo(FORGED_CREATED_AT);
    }
}
