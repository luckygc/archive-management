package github.luckygc.am.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ApiRequestPathsTests {

    @ParameterizedTest
    @ValueSource(
            strings = {
                "/approval-workflow-definitions",
                "/archive-items",
                "/archive-items:createExportDownloadLink",
                "/authentication-events",
                "/authentication-user-options",
                "/authentication-users",
                "/authorization-roles",
                "/file-links/code:download",
                "/intake",
                "/login-sessions",
                "/me",
                "/operations",
                "/operations/archive-search-projection-rebuild-17",
                "/organization-departments",
                "/public-file-links/code:download",
                "/totp-credentials",
                "/unified-todos",
                "/workspace-summary"
            })
    void recognizesApiResourcePaths(String path) {
        assertThat(ApiRequestPaths.isApiRequest(path)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "/",
                "/login",
                "/authentication-error",
                "/archive/intake",
                "/approval/center",
                "/assets/app.js",
                "/actuator/health",
                "/v3/api-docs",
                "/api/v1/archive-items",
                "/v1/archive-items"
            })
    void excludesPageAndFormerApiPaths(String path) {
        assertThat(ApiRequestPaths.isApiRequest(path)).isFalse();
    }
}
