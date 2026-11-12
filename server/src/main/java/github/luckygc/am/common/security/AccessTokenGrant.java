package github.luckygc.am.common.security;

import java.util.Set;

import org.jspecify.annotations.Nullable;

/** 当前请求已认证访问令牌的约束。scope 是主体权限的子集，数据范围不由 scope 再收窄。 */
public record AccessTokenGrant(
        long tokenId,
        AccessTokenSubjectType subjectType,
        long subjectId,
        @Nullable Long representedUserId,
        Set<String> scopes) {

    public AccessTokenGrant {
        scopes = Set.copyOf(scopes);
    }
}
