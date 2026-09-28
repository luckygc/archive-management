package github.luckygc.am.infrastructure.security;

import java.util.Set;

public final class ApiRequestPaths {

    private static final Set<String> EXACT_RESOURCE_ROOTS =
            Set.of(
                    "authentication-events",
                    "authentication-user-options",
                    "authentication-users",
                    "file-links",
                    "intake",
                    "me",
                    "operations",
                    "public-file-links",
                    "unified-todos",
                    "workspace-summary");

    private ApiRequestPaths() {}

    public static boolean isApiRequest(String uri) {
        if (uri == null || !uri.startsWith("/")) {
            return false;
        }
        int end = uri.length();
        for (char separator : new char[] {'/', ':'}) {
            int index = uri.indexOf(separator, 1);
            if (index >= 0 && index < end) {
                end = index;
            }
        }
        String root = uri.substring(1, end);
        return EXACT_RESOURCE_ROOTS.contains(root)
                || root.startsWith("approval-")
                || root.startsWith("archive-")
                || root.startsWith("authorization-")
                || root.startsWith("login-")
                || root.startsWith("organization-")
                || root.startsWith("totp-");
    }
}
