package github.luckygc.am.infrastructure.hibernate;

import org.hibernate.Interceptor;
import org.hibernate.type.Type;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import github.luckygc.am.common.audit.CreationAuditable;
import github.luckygc.am.common.audit.CreationTimeAuditable;
import github.luckygc.am.common.audit.UpdateAuditable;
import github.luckygc.am.common.audit.UpdateTimeAuditable;
import github.luckygc.am.infrastructure.audit.AuditContext;
import github.luckygc.am.infrastructure.audit.AuditContextProvider;

@Component
public class SecurityAuditingInterceptor implements Interceptor {

    private final AuditContextProvider auditContextProvider;

    public SecurityAuditingInterceptor(AuditContextProvider auditContextProvider) {
        this.auditContextProvider = auditContextProvider;
    }

    @Override
    public void onInsert(
            Object entity,
            Object id,
            Object[] state,
            String[] propertyNames,
            Type[] propertyTypes) {
        if (!(entity instanceof CreationTimeAuditable)
                && !(entity instanceof UpdateTimeAuditable)) {
            return;
        }
        AuditContext context = auditContextProvider.current();
        fillCreationAudit(entity, context, state, propertyNames);
        fillUpdateAudit(entity, context, state, propertyNames);
    }

    @Override
    public void onUpdate(
            Object entity,
            Object id,
            Object[] state,
            String[] propertyNames,
            Type[] propertyTypes) {
        if (!(entity instanceof UpdateTimeAuditable)) {
            return;
        }
        fillUpdateAudit(entity, auditContextProvider.current(), state, propertyNames);
    }

    private void fillCreationAudit(
            Object entity, AuditContext context, Object[] state, String[] propertyNames) {
        if (entity instanceof CreationTimeAuditable auditable) {
            auditable.setCreatedAt(context.now());
            setState(state, propertyNames, auditable.createdAtPropertyName(), context.now());
        }
        if (entity instanceof CreationAuditable auditable) {
            auditable.setCreatedBy(context.userId());
            setState(state, propertyNames, "createdBy", context.userId());
        }
    }

    private void fillUpdateAudit(
            Object entity, AuditContext context, Object[] state, String[] propertyNames) {
        if (entity instanceof UpdateTimeAuditable auditable) {
            auditable.setUpdatedAt(context.now());
            setState(state, propertyNames, auditable.updatedAtPropertyName(), context.now());
        }
        if (entity instanceof UpdateAuditable auditable) {
            auditable.setUpdatedBy(context.userId());
            setState(state, propertyNames, "updatedBy", context.userId());
        }
    }

    private void setState(
            Object[] state, String[] propertyNames, String propertyName, @Nullable Object value) {
        for (int index = 0; index < propertyNames.length; index++) {
            if (propertyName.equals(propertyNames[index])) {
                state[index] = value;
                return;
            }
        }
        throw new IllegalStateException("Hibernate state 缺少审计属性: " + propertyName);
    }
}
