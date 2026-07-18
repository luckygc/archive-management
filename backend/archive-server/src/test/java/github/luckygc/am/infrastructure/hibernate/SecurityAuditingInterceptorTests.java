package github.luckygc.am.infrastructure.hibernate;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;

import org.hibernate.type.Type;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import github.luckygc.am.common.audit.CreationAuditable;
import github.luckygc.am.common.audit.CreationTimeAuditable;
import github.luckygc.am.common.audit.UpdateAuditable;
import github.luckygc.am.common.audit.UpdateTimeAuditable;
import github.luckygc.am.infrastructure.audit.AuditContext;
import github.luckygc.am.infrastructure.audit.AuditContextProvider;
import github.luckygc.am.module.storage.FileLink;
import github.luckygc.am.module.storage.StorageObject;

@ExtendWith(MockitoExtension.class)
@DisplayName("Hibernate 无状态会话审计拦截器")
class SecurityAuditingInterceptorTests {

    private static final LocalDateTime FORGED_CREATED_AT = LocalDateTime.of(2000, 1, 1, 0, 0);
    private static final LocalDateTime FORGED_UPDATED_AT = LocalDateTime.of(2001, 1, 1, 0, 0);
    private static final LocalDateTime AUDIT_TIME = LocalDateTime.of(2026, 7, 16, 10, 30);

    @Mock private AuditContextProvider auditContextProvider;

    @InjectMocks private SecurityAuditingInterceptor interceptor;

    @Test
    @DisplayName("插入时用同一审计上下文覆盖实体和 Hibernate state 的创建及更新字段")
    void insertOverwritesEntityAndStateWithOneAuditContext() {
        when(auditContextProvider.current()).thenReturn(new AuditContext(AUDIT_TIME, 42L));
        FullAuditable entity = new FullAuditable();
        Object[] state = {FORGED_CREATED_AT, 901L, FORGED_UPDATED_AT, 902L};
        String[] propertyNames = {"createdAt", "createdBy", "updatedAt", "updatedBy"};

        interceptor.onInsert(entity, 1L, state, propertyNames, propertyTypes(propertyNames));

        assertAll(
                () -> assertEquals(AUDIT_TIME, entity.getCreatedAt()),
                () -> assertEquals(42L, entity.getCreatedBy()),
                () -> assertEquals(AUDIT_TIME, entity.getUpdatedAt()),
                () -> assertEquals(42L, entity.getUpdatedBy()),
                () -> assertEquals(AUDIT_TIME, state[0]),
                () -> assertEquals(42L, state[1]),
                () -> assertEquals(AUDIT_TIME, state[2]),
                () -> assertEquals(42L, state[3]));
        verify(auditContextProvider).current();
    }

    @Test
    @DisplayName("更新时只覆盖更新字段并且每次回调只读取一次上下文")
    void updateOnlyOverwritesUpdateFieldsWithOneAuditContext() {
        when(auditContextProvider.current()).thenReturn(new AuditContext(AUDIT_TIME, 43L));
        FullAuditable entity = new FullAuditable();
        Object[] state = {FORGED_CREATED_AT, 901L, FORGED_UPDATED_AT, 902L};
        String[] propertyNames = {"createdAt", "createdBy", "updatedAt", "updatedBy"};

        interceptor.onUpdate(entity, 1L, state, propertyNames, propertyTypes(propertyNames));

        assertAll(
                () -> assertEquals(FORGED_CREATED_AT, entity.getCreatedAt()),
                () -> assertEquals(901L, entity.getCreatedBy()),
                () -> assertEquals(AUDIT_TIME, entity.getUpdatedAt()),
                () -> assertEquals(43L, entity.getUpdatedBy()),
                () -> assertEquals(FORGED_CREATED_AT, state[0]),
                () -> assertEquals(901L, state[1]),
                () -> assertEquals(AUDIT_TIME, state[2]),
                () -> assertEquals(43L, state[3]));
        verify(auditContextProvider).current();
    }

    @Test
    @DisplayName("未认证上下文仍写入时间并将伪造用户覆盖为 null")
    void nullUserStillWritesTimeAndClearsForgedUsers() {
        when(auditContextProvider.current()).thenReturn(new AuditContext(AUDIT_TIME, null));
        FullAuditable entity = new FullAuditable();
        Object[] state = {FORGED_CREATED_AT, 901L, FORGED_UPDATED_AT, 902L};
        String[] propertyNames = {"createdAt", "createdBy", "updatedAt", "updatedBy"};

        interceptor.onInsert(entity, 1L, state, propertyNames, propertyTypes(propertyNames));

        assertAll(
                () -> assertEquals(AUDIT_TIME, entity.getCreatedAt()),
                () -> assertNull(entity.getCreatedBy()),
                () -> assertEquals(AUDIT_TIME, entity.getUpdatedAt()),
                () -> assertNull(entity.getUpdatedBy()),
                () -> assertEquals(AUDIT_TIME, state[0]),
                () -> assertNull(state[1]),
                () -> assertEquals(AUDIT_TIME, state[2]),
                () -> assertNull(state[3]));
        verify(auditContextProvider).current();
    }

    @Test
    @DisplayName("纯时间实体无需用户字段也能初始化创建和更新时间")
    void pureTimeEntityDoesNotNeedUserFields() {
        when(auditContextProvider.current()).thenReturn(new AuditContext(AUDIT_TIME, 44L));
        TimeOnlyAuditable entity = new TimeOnlyAuditable();
        Object[] state = {FORGED_CREATED_AT, FORGED_UPDATED_AT};
        String[] propertyNames = {"createdAt", "updatedAt"};

        interceptor.onInsert(entity, 1L, state, propertyNames, propertyTypes(propertyNames));

        assertAll(
                () -> assertEquals(AUDIT_TIME, entity.getCreatedAt()),
                () -> assertEquals(AUDIT_TIME, entity.getUpdatedAt()),
                () -> assertEquals(AUDIT_TIME, state[0]),
                () -> assertEquals(AUDIT_TIME, state[1]));
        verify(auditContextProvider).current();
    }

    @Test
    @DisplayName("业务命名的插入时间通过接口 property name 同步实体和 state")
    void specialCreationTimePropertyNameSynchronizesEntityAndState() {
        when(auditContextProvider.current()).thenReturn(new AuditContext(AUDIT_TIME, 45L));
        OperatedAtAuditable entity = new OperatedAtAuditable();
        Object[] state = {FORGED_CREATED_AT};
        String[] propertyNames = {"operatedAt"};

        interceptor.onInsert(entity, 1L, state, propertyNames, propertyTypes(propertyNames));

        assertAll(
                () -> assertEquals(AUDIT_TIME, entity.getCreatedAt()),
                () -> assertEquals(AUDIT_TIME, state[0]));
        verify(auditContextProvider).current();
    }

    @Test
    @DisplayName("审计实体缺少对应 Hibernate property 时立即失败")
    void missingAuditPropertyFailsFast() {
        when(auditContextProvider.current()).thenReturn(new AuditContext(AUDIT_TIME, 46L));
        OperatedAtAuditable entity = new OperatedAtAuditable();
        Object[] state = {"unchanged"};
        String[] propertyNames = {"name"};

        IllegalStateException exception =
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                interceptor.onInsert(
                                        entity,
                                        1L,
                                        state,
                                        propertyNames,
                                        propertyTypes(propertyNames)));

        assertEquals(true, exception.getMessage().contains("operatedAt"));
        verify(auditContextProvider).current();
    }

    @Test
    @DisplayName("非审计实体不读取审计上下文")
    void nonAuditableEntityDoesNotReadAuditContext() {
        Object[] state = {"unchanged"};
        String[] propertyNames = {"name"};

        interceptor.onInsert(new Object(), 1L, state, propertyNames, propertyTypes(propertyNames));

        assertEquals("unchanged", state[0]);
        verifyNoInteractions(auditContextProvider);
    }

    @Test
    @DisplayName("安全主体与对象所有者不同时只覆盖存储对象创建时间")
    void storageObjectOwnerIsNotGenericCreationAuditUser() {
        when(auditContextProvider.current()).thenReturn(new AuditContext(AUDIT_TIME, 99L));
        StorageObject entity = new StorageObject();
        entity.setCreatedAt(FORGED_CREATED_AT);
        entity.setCreatedBy(9L);
        Object[] state = {FORGED_CREATED_AT, 9L};
        String[] propertyNames = {"createdAt", "createdBy"};

        interceptor.onInsert(entity, 1L, state, propertyNames, propertyTypes(propertyNames));

        assertAll(
                () -> assertEquals(AUDIT_TIME, entity.getCreatedAt()),
                () -> assertEquals(9L, entity.getCreatedBy()),
                () -> assertEquals(AUDIT_TIME, state[0]),
                () -> assertEquals(9L, state[1]));
        verify(auditContextProvider).current();
    }

    @Test
    @DisplayName("无安全主体时仍保留文件短链显式创建人")
    void fileLinkOwnerSurvivesMissingSecurityUser() {
        when(auditContextProvider.current()).thenReturn(new AuditContext(AUDIT_TIME, null));
        FileLink entity = new FileLink();
        entity.setCreatedAt(FORGED_CREATED_AT);
        entity.setCreatedBy(8L);
        Object[] state = {FORGED_CREATED_AT, 8L};
        String[] propertyNames = {"createdAt", "createdBy"};

        interceptor.onInsert(entity, 1L, state, propertyNames, propertyTypes(propertyNames));

        assertAll(
                () -> assertEquals(AUDIT_TIME, entity.getCreatedAt()),
                () -> assertEquals(8L, entity.getCreatedBy()),
                () -> assertEquals(AUDIT_TIME, state[0]),
                () -> assertEquals(8L, state[1]));
        verify(auditContextProvider).current();
    }

    private static Type[] propertyTypes(String[] propertyNames) {
        return new Type[propertyNames.length];
    }

    private static final class FullAuditable implements CreationAuditable, UpdateAuditable {

        private LocalDateTime createdAt = FORGED_CREATED_AT;
        private @Nullable Long createdBy = 901L;
        private LocalDateTime updatedAt = FORGED_UPDATED_AT;
        private @Nullable Long updatedBy = 902L;

        @Override
        public LocalDateTime getCreatedAt() {
            return createdAt;
        }

        @Override
        public void setCreatedAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
        }

        @Override
        public @Nullable Long getCreatedBy() {
            return createdBy;
        }

        @Override
        public void setCreatedBy(@Nullable Long createdBy) {
            this.createdBy = createdBy;
        }

        @Override
        public LocalDateTime getUpdatedAt() {
            return updatedAt;
        }

        @Override
        public void setUpdatedAt(LocalDateTime updatedAt) {
            this.updatedAt = updatedAt;
        }

        @Override
        public @Nullable Long getUpdatedBy() {
            return updatedBy;
        }

        @Override
        public void setUpdatedBy(@Nullable Long updatedBy) {
            this.updatedBy = updatedBy;
        }
    }

    private static final class TimeOnlyAuditable
            implements CreationTimeAuditable, UpdateTimeAuditable {

        private LocalDateTime createdAt = FORGED_CREATED_AT;
        private LocalDateTime updatedAt = FORGED_UPDATED_AT;

        @Override
        public LocalDateTime getCreatedAt() {
            return createdAt;
        }

        @Override
        public void setCreatedAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
        }

        @Override
        public LocalDateTime getUpdatedAt() {
            return updatedAt;
        }

        @Override
        public void setUpdatedAt(LocalDateTime updatedAt) {
            this.updatedAt = updatedAt;
        }
    }

    private static final class OperatedAtAuditable implements CreationTimeAuditable {

        private LocalDateTime operatedAt = FORGED_CREATED_AT;

        @Override
        public LocalDateTime getCreatedAt() {
            return operatedAt;
        }

        @Override
        public void setCreatedAt(LocalDateTime createdAt) {
            operatedAt = createdAt;
        }

        @Override
        public String createdAtPropertyName() {
            return "operatedAt";
        }
    }
}
