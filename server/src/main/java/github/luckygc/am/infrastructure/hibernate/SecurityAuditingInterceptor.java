package github.luckygc.am.infrastructure.hibernate;

import org.hibernate.Interceptor;
import org.hibernate.type.Type;
import org.jspecify.annotations.Nullable;
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
        fillCreatedBy(entity, state, propertyNames);
        fillUpdatedByIfMissing(entity, state, propertyNames);
    }

    @Override
    public void onUpdate(
            Object entity,
            Object id,
            Object[] state,
            String[] propertyNames,
            Type[] propertyTypes) {
        fillUpdatedBy(entity, state, propertyNames);
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

    private @Nullable Long currentUserId() {
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
