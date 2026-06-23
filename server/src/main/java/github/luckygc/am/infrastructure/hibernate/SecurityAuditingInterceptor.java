package github.luckygc.am.infrastructure.hibernate;

import java.time.LocalDateTime;

import org.hibernate.Interceptor;
import org.hibernate.type.Type;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import github.luckygc.am.common.audit.CreationAuditable;
import github.luckygc.am.common.audit.UpdateAuditable;
import github.luckygc.am.common.security.AuthenticatedUser;

@Component
public class SecurityAuditingInterceptor implements Interceptor {

    @Override
    public void onInsert(
            Object entity,
            Object id,
            Object[] state,
            String[] propertyNames,
            Type[] propertyTypes) {
        LocalDateTime now = LocalDateTime.now();
        fillCreated(entity, state, propertyNames, now);
        fillUpdatedIfMissing(entity, state, propertyNames, now);
    }

    @Override
    public void onUpdate(
            Object entity,
            Object id,
            Object[] state,
            String[] propertyNames,
            Type[] propertyTypes) {
        fillUpdated(entity, state, propertyNames, LocalDateTime.now());
    }

    @Override
    public void onUpsert(
            Object entity,
            Object id,
            Object[] state,
            String[] propertyNames,
            Type[] propertyTypes) {
        fillUpdated(entity, state, propertyNames, LocalDateTime.now());
    }

    private void fillCreated(
            Object entity, Object[] state, String[] propertyNames, LocalDateTime now) {
        fillCreatedAt(entity, state, propertyNames, now);
        fillCreatedBy(entity, state, propertyNames);
    }

    private void fillUpdatedIfMissing(
            Object entity, Object[] state, String[] propertyNames, LocalDateTime now) {
        fillUpdatedAtIfMissing(entity, state, propertyNames, now);
        fillUpdatedByIfMissing(entity, state, propertyNames);
    }

    private void fillUpdated(
            Object entity, Object[] state, String[] propertyNames, LocalDateTime now) {
        fillUpdatedAt(entity, state, propertyNames, now);
        fillUpdatedBy(entity, state, propertyNames);
    }

    private void fillCreatedAt(
            Object entity, Object[] state, String[] propertyNames, LocalDateTime now) {
        if (!(entity instanceof CreationAuditable auditable) || auditable.getCreatedAt() != null) {
            return;
        }
        auditable.setCreatedAt(now);
        setState(state, propertyNames, "createdAt", now);
    }

    private void fillUpdatedAtIfMissing(
            Object entity, Object[] state, String[] propertyNames, LocalDateTime now) {
        if (!(entity instanceof UpdateAuditable auditable) || auditable.getUpdatedAt() != null) {
            return;
        }
        auditable.setUpdatedAt(now);
        setState(state, propertyNames, "updatedAt", now);
    }

    private void fillUpdatedAt(
            Object entity, Object[] state, String[] propertyNames, LocalDateTime now) {
        if (!(entity instanceof UpdateAuditable auditable)) {
            return;
        }
        auditable.setUpdatedAt(now);
        setState(state, propertyNames, "updatedAt", now);
    }

    private void fillCreatedBy(Object entity, Object[] state, String[] propertyNames) {
        Long userId = currentUserId();
        if (userId == null || !(entity instanceof CreationAuditable auditable)) {
            return;
        }
        if (auditable.getCreatedBy() == null) {
            auditable.setCreatedBy(userId);
            setState(state, propertyNames, "createdBy", userId);
        }
    }

    private void fillUpdatedByIfMissing(Object entity, Object[] state, String[] propertyNames) {
        Long userId = currentUserId();
        if (userId == null || !(entity instanceof UpdateAuditable auditable)) {
            return;
        }
        if (auditable.getUpdatedBy() == null) {
            auditable.setUpdatedBy(userId);
            setState(state, propertyNames, "updatedBy", userId);
        }
    }

    private void fillUpdatedBy(Object entity, Object[] state, String[] propertyNames) {
        Long userId = currentUserId();
        if (userId == null || !(entity instanceof UpdateAuditable auditable)) {
            return;
        }
        auditable.setUpdatedBy(userId);
        setState(state, propertyNames, "updatedBy", userId);
    }

    private Long currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            return null;
        }
        return user.id();
    }

    private void setState(
            Object[] state, String[] propertyNames, String propertyName, Object value) {
        for (int index = 0; index < propertyNames.length; index++) {
            if (propertyName.equals(propertyNames[index])) {
                state[index] = value;
                return;
            }
        }
    }
}
