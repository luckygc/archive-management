package github.luckygc.am.module.authentication.service;

import jakarta.data.page.CursoredPage;
import jakarta.data.page.PageRequest;

import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.annotation.JsonInclude;

import github.luckygc.am.common.api.CursorPageResponse;
import github.luckygc.am.common.exception.BadRequestException;
import github.luckygc.am.module.authentication.AccessTokenCall;
import github.luckygc.am.module.authentication.AccessTokenFailureReason;
import github.luckygc.am.module.authentication.repository.AccessTokenCallDataRepository;
import github.luckygc.am.module.authorization.service.AuthorizationPermissionCode;
import github.luckygc.am.module.authorization.service.AuthorizationPermissionService;

@Service
public class AccessTokenCallService {

    private final AccessTokenCallDataRepository callRepository;
    private final AuthorizationPermissionService permissionService;

    public AccessTokenCallService(
            AccessTokenCallDataRepository callRepository,
            AuthorizationPermissionService permissionService) {
        this.callRepository = callRepository;
        this.permissionService = permissionService;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(AccessTokenCallCommand command) {
        AccessTokenCall call = new AccessTokenCall();
        call.setTokenId(command.tokenId());
        call.setTokenPrefix(truncate(command.tokenPrefix(), 16));
        call.setCallerType(command.callerType());
        call.setServiceAccountId(command.serviceAccountId());
        call.setRepresentedUserId(command.representedUserId());
        call.setHttpMethod(truncate(command.httpMethod(), 16));
        call.setRequestPath(truncate(command.requestPath(), 512));
        call.setStatusCode(command.statusCode());
        call.setDurationMs(Math.max(command.durationMs(), 0));
        call.setSourceIp(truncate(command.sourceIp(), 64));
        call.setTraceId(truncate(command.traceId(), 128));
        call.setFailureReason(command.failureReason());
        callRepository.insert(call);
    }

    @Transactional(readOnly = true)
    public CursorPageResponse<AccessTokenCallResponse> list(
            @Nullable Long serviceAccountId,
            @Nullable Long tokenId,
            PageRequest pageRequest,
            Long operatorUserId) {
        permissionService.requirePermission(
                operatorUserId, AuthorizationPermissionCode.AUTHENTICATION_API_CALL_READ);
        if (serviceAccountId != null && serviceAccountId <= 0) {
            throw new BadRequestException("服务账号 ID 不合法", "serviceAccountId", "服务账号 ID 不合法");
        }
        if (tokenId != null && tokenId <= 0) {
            throw new BadRequestException("令牌 ID 不合法", "tokenId", "令牌 ID 不合法");
        }
        CursoredPage<AccessTokenCall> page = query(serviceAccountId, tokenId, pageRequest);
        return CursorPageResponse.from(page, pageRequest, AccessTokenCallResponse::from);
    }

    private CursoredPage<AccessTokenCall> query(
            @Nullable Long serviceAccountId, @Nullable Long tokenId, PageRequest pageRequest) {
        if (serviceAccountId != null && tokenId != null) {
            return callRepository.findByServiceAccountIdAndTokenId(
                    serviceAccountId, tokenId, pageRequest);
        }
        if (serviceAccountId != null) {
            return callRepository.findByServiceAccountId(serviceAccountId, pageRequest);
        }
        if (tokenId != null) {
            return callRepository.findByTokenId(tokenId, pageRequest);
        }
        return callRepository.findAll(pageRequest);
    }

    private @Nullable String truncate(@Nullable String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String trimmed = StringUtils.trimToNull(value);
        if (trimmed == null) {
            return null;
        }
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }

    public record AccessTokenCallCommand(
            @Nullable Long tokenId,
            @Nullable String tokenPrefix,
            String callerType,
            @Nullable Long serviceAccountId,
            @Nullable Long representedUserId,
            String httpMethod,
            String requestPath,
            int statusCode,
            long durationMs,
            @Nullable String sourceIp,
            @Nullable String traceId,
            @Nullable AccessTokenFailureReason failureReason) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AccessTokenCallResponse(
            Long id,
            @Nullable Long tokenId,
            @Nullable String tokenPrefix,
            String callerType,
            @Nullable Long serviceAccountId,
            @Nullable Long representedUserId,
            String httpMethod,
            String requestPath,
            int statusCode,
            long durationMs,
            @Nullable String sourceIp,
            @Nullable String traceId,
            @Nullable String failureReason,
            String occurredAt) {

        static AccessTokenCallResponse from(AccessTokenCall call) {
            return new AccessTokenCallResponse(
                    call.getId(),
                    call.getTokenId(),
                    call.getTokenPrefix(),
                    call.getCallerType(),
                    call.getServiceAccountId(),
                    call.getRepresentedUserId(),
                    call.getHttpMethod(),
                    call.getRequestPath(),
                    call.getStatusCode(),
                    call.getDurationMs(),
                    call.getSourceIp(),
                    call.getTraceId(),
                    call.getFailureReason() == null ? null : call.getFailureReason().name(),
                    call.getCreatedAt() == null ? "" : call.getCreatedAt().toString());
        }
    }
}
