package github.luckygc.am.module.authentication.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import jakarta.data.page.CursoredPage;
import jakarta.data.page.PageRequest;

import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.annotation.JsonInclude;

import github.luckygc.am.common.api.CursorPageResponse;
import github.luckygc.am.common.exception.BadRequestException;
import github.luckygc.am.common.security.MachineAccessPrincipal;
import github.luckygc.am.module.authentication.AccessToken;
import github.luckygc.am.module.authentication.AccessTokenSecrets;
import github.luckygc.am.module.authentication.AccessTokenType;
import github.luckygc.am.module.authentication.AuthenticationUser;
import github.luckygc.am.module.authentication.ServiceAccount;
import github.luckygc.am.module.authentication.repository.AccessTokenDataRepository;
import github.luckygc.am.module.authentication.repository.AuthenticationUserDataRepository;
import github.luckygc.am.module.authentication.repository.ServiceAccountDataRepository;
import github.luckygc.am.module.authorization.service.AuthorizationPermissionCode;
import github.luckygc.am.module.authorization.service.AuthorizationPermissionService;

@Service
public class AccessTokenManagementService {

    private static final int MAX_EXPIRY_DAYS = 366;

    private final AccessTokenDataRepository tokenRepository;
    private final ServiceAccountDataRepository serviceAccountRepository;
    private final AuthenticationUserDataRepository userRepository;
    private final AuthorizationPermissionService permissionService;
    private final Clock clock;

    public AccessTokenManagementService(
            AccessTokenDataRepository tokenRepository,
            ServiceAccountDataRepository serviceAccountRepository,
            AuthenticationUserDataRepository userRepository,
            AuthorizationPermissionService permissionService,
            Clock clock) {
        this.tokenRepository = tokenRepository;
        this.serviceAccountRepository = serviceAccountRepository;
        this.userRepository = userRepository;
        this.permissionService = permissionService;
        this.clock = clock;
    }

    @Transactional
    public IssuedAccessTokenResponse issueForServiceAccount(
            Long serviceAccountId, CreateAccessTokenRequest request, Long operatorUserId) {
        permissionService.requirePermission(
                operatorUserId, AuthorizationPermissionCode.AUTHENTICATION_ACCESS_TOKEN_MANAGE);
        ServiceAccount account = requireServiceAccount(serviceAccountId);
        List<String> allowed = permissionService.listServiceAccountPermissionCodes(account.getId());
        return issue(AccessTokenType.SERVICE_ACCOUNT, account.getId(), null, request, allowed);
    }

    @Transactional
    public IssuedAccessTokenResponse issueForUser(
            CreateAccessTokenRequest request, Long userId, @Nullable Object principal) {
        if (principal instanceof MachineAccessPrincipal) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "服务账号不能签发个人访问令牌");
        }
        AuthenticationUser user =
                userRepository
                        .findById(userId)
                        .orElseThrow(
                                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在"));
        if (!user.isEnabled()) {
            throw new BadRequestException("用户已停用", "userId", "用户已停用");
        }
        return issue(
                AccessTokenType.USER,
                null,
                user.getId(),
                request,
                permissionService.listUserPermissionCodes(user.getId()));
    }

    @Transactional(readOnly = true)
    public CursorPageResponse<AccessTokenResponse> listForServiceAccount(
            Long serviceAccountId, PageRequest pageRequest, Long operatorUserId) {
        permissionService.requirePermission(
                operatorUserId, AuthorizationPermissionCode.AUTHENTICATION_ACCESS_TOKEN_MANAGE);
        requireServiceAccount(serviceAccountId);
        CursoredPage<AccessToken> page =
                tokenRepository.findByServiceAccountId(serviceAccountId, pageRequest);
        return CursorPageResponse.from(page, pageRequest, AccessTokenResponse::from);
    }

    @Transactional(readOnly = true)
    public CursorPageResponse<AccessTokenResponse> listForUser(
            PageRequest pageRequest, Long userId, @Nullable Object principal) {
        if (principal instanceof MachineAccessPrincipal) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "服务账号没有个人访问令牌");
        }
        CursoredPage<AccessToken> page =
                tokenRepository.findByUserIdAndTokenType(userId, AccessTokenType.USER, pageRequest);
        return CursorPageResponse.from(page, pageRequest, AccessTokenResponse::from);
    }

    @Transactional
    public void revoke(Long tokenId, Long operatorUserId, @Nullable Object principal) {
        AccessToken token =
                tokenRepository
                        .findById(tokenId)
                        .orElseThrow(
                                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "访问令牌不存在"));
        boolean self =
                token.getTokenType() == AccessTokenType.USER
                        && token.getUserId() != null
                        && token.getUserId().equals(operatorUserId)
                        && !(principal instanceof MachineAccessPrincipal);
        if (!self) {
            permissionService.requirePermission(
                    operatorUserId, AuthorizationPermissionCode.AUTHENTICATION_ACCESS_TOKEN_MANAGE);
        }
        if (token.getRevokedAt() == null) {
            token.setRevokedAt(LocalDateTime.now(clock));
            tokenRepository.update(token);
        }
    }

    private IssuedAccessTokenResponse issue(
            AccessTokenType tokenType,
            @Nullable Long serviceAccountId,
            @Nullable Long userId,
            CreateAccessTokenRequest request,
            List<String> allowedPermissions) {
        String name = requireName(request.name());
        LocalDateTime expiresAt = requireExpiry(request.expiresAt());
        String scopes = resolveScopes(request.scopes(), allowedPermissions);
        AccessTokenSecrets.IssuedToken issued =
                insertToken(tokenType, serviceAccountId, userId, name, expiresAt, scopes);
        AccessToken token = tokenRepository.findByTokenPrefix(issued.prefix());
        if (token == null) {
            throw new IllegalStateException("访问令牌签发后无法读取");
        }
        return IssuedAccessTokenResponse.from(token, issued.secret());
    }

    private AccessTokenSecrets.IssuedToken insertToken(
            AccessTokenType tokenType,
            @Nullable Long serviceAccountId,
            @Nullable Long userId,
            String name,
            LocalDateTime expiresAt,
            String scopes) {
        DataIntegrityViolationException lastConflict = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            AccessTokenSecrets.IssuedToken issued = AccessTokenSecrets.issue();
            AccessToken token = new AccessToken();
            token.setTokenType(tokenType);
            token.setServiceAccountId(serviceAccountId);
            token.setUserId(userId);
            token.setName(name);
            token.setTokenPrefix(issued.prefix());
            token.setTokenHash(issued.hash());
            token.setScopes(scopes);
            token.setExpiresAt(expiresAt);
            try {
                tokenRepository.insert(token);
                return issued;
            } catch (DataIntegrityViolationException exception) {
                lastConflict = exception;
            }
        }
        throw new IllegalStateException("访问令牌前缀冲突", lastConflict);
    }

    private String resolveScopes(
            @Nullable List<String> requested, List<String> allowedPermissions) {
        Set<String> allowed = new LinkedHashSet<>(allowedPermissions);
        List<String> selected = new ArrayList<>();
        if (requested == null) {
            if (allowed.contains(AccessTokenSecrets.SUGGESTED_READ_ONLY_SCOPE)) {
                selected.add(AccessTokenSecrets.SUGGESTED_READ_ONLY_SCOPE);
            }
        } else {
            for (String scope : requested) {
                String normalized = StringUtils.trimToNull(scope);
                if (normalized == null) {
                    continue;
                }
                if (!allowed.contains(normalized)) {
                    throw new BadRequestException(
                            "scope 超出主体权限", "scopes", "scope 不是主体已有权限: " + normalized);
                }
                selected.add(normalized);
            }
        }
        if (selected.isEmpty()) {
            throw new BadRequestException(
                    "令牌 scope 不能为空", "scopes", "未指定 scope，且主体没有默认只读权限 archive:item:read");
        }
        String encoded = AccessTokenSecrets.encodeScopes(selected);
        if (encoded.length() > 2000) {
            throw new BadRequestException("scope 过多", "scopes", "scope 过多");
        }
        return encoded;
    }

    private LocalDateTime requireExpiry(LocalDateTime expiresAt) {
        if (expiresAt == null) {
            throw new BadRequestException("过期时间不能为空", "expiresAt", "过期时间不能为空");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        if (!expiresAt.isAfter(now)) {
            throw new BadRequestException("过期时间必须晚于当前时间", "expiresAt", "过期时间必须晚于当前时间");
        }
        if (expiresAt.isAfter(now.plusDays(MAX_EXPIRY_DAYS))) {
            throw new BadRequestException("过期时间不能超过 366 天", "expiresAt", "过期时间不能超过 366 天");
        }
        return expiresAt;
    }

    private String requireName(String name) {
        String normalized = StringUtils.trimToNull(name);
        if (normalized == null || normalized.length() > 100) {
            throw new BadRequestException("令牌名称不合法", "name", "名称不能为空且不超过 100 个字符");
        }
        return normalized;
    }

    private ServiceAccount requireServiceAccount(Long serviceAccountId) {
        ServiceAccount account =
                serviceAccountRepository
                        .findById(serviceAccountId)
                        .orElseThrow(
                                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "服务账号不存在"));
        return account;
    }

    public record CreateAccessTokenRequest(
            String name, LocalDateTime expiresAt, @Nullable List<String> scopes) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AccessTokenResponse(
            Long id,
            String name,
            String tokenType,
            @Nullable Long serviceAccountId,
            @Nullable Long userId,
            String tokenPrefix,
            List<String> scopes,
            String expiresAt,
            @Nullable String revokedAt,
            @Nullable String lastUsedAt,
            String createdAt) {

        static AccessTokenResponse from(AccessToken token) {
            return new AccessTokenResponse(
                    token.getId(),
                    token.getName(),
                    token.getTokenType().name(),
                    token.getServiceAccountId(),
                    token.getUserId(),
                    token.getTokenPrefix(),
                    List.copyOf(AccessTokenSecrets.decodeScopes(token.getScopes())),
                    token.getExpiresAt().toString(),
                    token.getRevokedAt() == null ? null : token.getRevokedAt().toString(),
                    token.getLastUsedAt() == null ? null : token.getLastUsedAt().toString(),
                    token.getCreatedAt() == null ? "" : token.getCreatedAt().toString());
        }
    }

    public record IssuedAccessTokenResponse(
            Long id,
            String name,
            String tokenType,
            @Nullable Long serviceAccountId,
            @Nullable Long userId,
            String tokenPrefix,
            List<String> scopes,
            String expiresAt,
            String createdAt,
            String secret) {

        static IssuedAccessTokenResponse from(AccessToken token, String secret) {
            AccessTokenResponse response = AccessTokenResponse.from(token);
            return new IssuedAccessTokenResponse(
                    response.id(),
                    response.name(),
                    response.tokenType(),
                    response.serviceAccountId(),
                    response.userId(),
                    response.tokenPrefix(),
                    response.scopes(),
                    response.expiresAt(),
                    response.createdAt(),
                    secret);
        }
    }
}
