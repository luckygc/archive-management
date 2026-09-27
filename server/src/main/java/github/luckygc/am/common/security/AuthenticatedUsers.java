package github.luckygc.am.common.security;

import org.jspecify.annotations.Nullable;

public final class AuthenticatedUsers {

    private AuthenticatedUsers() {}

    public static @Nullable Long currentUserId(@Nullable Object principal) {
        if (!(principal instanceof AuthenticatedUser user)) {
            return null;
        }
        return user.id();
    }

    public static Long requireUserId(@Nullable Object principal) {
        return requireResolvedUserId(currentUserId(principal));
    }

    public static Long requireResolvedUserId(@Nullable Long userId) {
        if (userId == null) {
            throw new UnauthenticatedException();
        }
        return userId;
    }
}
