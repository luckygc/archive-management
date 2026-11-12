package github.luckygc.am.module.authentication;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("访问令牌明文")
class AccessTokenSecretsTests {

    @Test
    @DisplayName("签发结果可按前缀定位，且哈希比对拒绝错误秘密")
    void issueMatchesOnlyTheOriginalSecret() {
        AccessTokenSecrets.IssuedToken issued = AccessTokenSecrets.issue();

        assertThat(issued.prefix()).hasSize(16);
        assertThat(issued.secret()).startsWith("am1_" + issued.prefix() + "_");
        assertThat(AccessTokenSecrets.prefixOf(issued.secret())).isEqualTo(issued.prefix());
        assertThat(AccessTokenSecrets.hash(issued.secret())).isEqualTo(issued.hash());
        assertThat(AccessTokenSecrets.matches(issued.secret(), issued.hash())).isTrue();
        assertThat(AccessTokenSecrets.matches(issued.secret() + "x", issued.hash())).isFalse();
        assertThat(AccessTokenSecrets.matches(issued.secret(), null)).isFalse();
        assertThat(AccessTokenSecrets.prefixOf("not-a-token")).isNull();
        assertThat(AccessTokenSecrets.prefixOf("am1_zzzzzzzzzzzzzzzz_secret")).isNull();
    }

    @Test
    @DisplayName("scope 编码去空、排序且可还原")
    void encodeScopesSortsAndDropsBlanks() {
        assertThat(
                        AccessTokenSecrets.encodeScopes(
                                List.of(" archive:item:create ", "", "archive:item:read")))
                .isEqualTo("archive:item:create,archive:item:read");
        assertThat(AccessTokenSecrets.decodeScopes("archive:item:read, archive:item:create"))
                .containsExactlyInAnyOrder("archive:item:read", "archive:item:create");
        assertThat(AccessTokenSecrets.decodeScopes(null)).isEmpty();
    }
}
