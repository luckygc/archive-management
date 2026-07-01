package github.luckygc.am.module.authentication.service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import jakarta.data.Order;
import jakarta.data.page.CursoredPage;
import jakarta.data.restrict.Restrict;
import jakarta.data.restrict.Restriction;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import github.luckygc.am.common.api.CursorPageRequest;
import github.luckygc.am.common.api.CursorPageResponse;
import github.luckygc.am.common.exception.BadRequestException;
import github.luckygc.am.common.security.AuthenticatedUser;
import github.luckygc.am.module.authentication.AuthenticationLoginEventType;
import github.luckygc.am.module.authentication.AuthenticationLoginLog;
import github.luckygc.am.module.authentication.ClientInfo;
import github.luckygc.am.module.authentication.ClientRequestContext;
import github.luckygc.am.module.authentication.ClientRequestContextResolver;
import github.luckygc.am.module.authentication.SpringSessionRecord;
import github.luckygc.am.module.authentication._AuthenticationLoginLog;
import github.luckygc.am.module.authentication._SpringSessionRecord;
import github.luckygc.am.module.authentication.repository.AuthenticationLoginLogDataRepository;
import github.luckygc.am.module.authentication.repository.SpringSessionRecordDataRepository;

@Service
public class AuthenticationAuditService {

    public static final String CLIENT_CONTEXT_ATTRIBUTE = "github.luckygc.am.auth.CLIENT_CONTEXT";
    public static final String USER_ID_ATTRIBUTE = "github.luckygc.am.auth.USER_ID";
    public static final String DISPLAY_NAME_ATTRIBUTE = "github.luckygc.am.auth.DISPLAY_NAME";
    public static final String ROLES_ATTRIBUTE = "github.luckygc.am.auth.ROLES";

    private final AuthenticationLoginLogDataRepository loginLogRepository;
    private final SpringSessionRecordDataRepository springSessionRepository;
    private final ClientRequestContextResolver contextResolver;
    private final SessionRepository<? extends Session> sessionRepository;

    public AuthenticationAuditService(
            AuthenticationLoginLogDataRepository loginLogRepository,
            SpringSessionRecordDataRepository springSessionRepository,
            ClientRequestContextResolver contextResolver,
            SessionRepository<? extends Session> sessionRepository) {
        this.loginLogRepository = loginLogRepository;
        this.springSessionRepository = springSessionRepository;
        this.contextResolver = contextResolver;
        this.sessionRepository = sessionRepository;
    }

    @Transactional(rollbackFor = Throwable.class)
    public void recordLoginSuccess(HttpServletRequest request, Authentication authentication) {
        ClientRequestContext context = contextResolver.resolve(request);
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.setAttribute(CLIENT_CONTEXT_ATTRIBUTE, context);
            session.setAttribute(USER_ID_ATTRIBUTE, currentUserId(authentication));
            session.setAttribute(DISPLAY_NAME_ATTRIBUTE, displayName(authentication));
            session.setAttribute(ROLES_ATTRIBUTE, roles(authentication));
        }
        AuthenticationLoginLog log = baseLog(AuthenticationLoginEventType.LOGIN_SUCCESS, context);
        log.setUserId(currentUserId(authentication));
        log.setUsername(authentication.getName());
        log.setDisplayName(displayName(authentication));
        log.setSessionId(session == null ? null : session.getId());
        loginLogRepository.insert(log);
    }

    @Transactional(rollbackFor = Throwable.class)
    public void recordLoginFailure(HttpServletRequest request, String reason) {
        ClientRequestContext context = contextResolver.resolve(request);
        AuthenticationLoginLog log = baseLog(AuthenticationLoginEventType.LOGIN_FAILURE, context);
        log.setUsername(request.getParameter("username"));
        log.setFailureReason(reason);
        loginLogRepository.insert(log);
    }

    @Transactional(rollbackFor = Throwable.class)
    public void recordLogout(HttpServletRequest request, @Nullable Authentication authentication) {
        ClientRequestContext context = contextFromSessionOrRequest(request);
        AuthenticationLoginLog log = baseLog(AuthenticationLoginEventType.LOGOUT, context);
        if (authentication != null) {
            log.setUserId(currentUserId(authentication));
            log.setUsername(authentication.getName());
            log.setDisplayName(displayName(authentication));
        }
        HttpSession session = request.getSession(false);
        log.setSessionId(session == null ? null : session.getId());
        loginLogRepository.insert(log);
    }

    @Transactional(rollbackFor = Throwable.class)
    public void revokeSession(
            String sessionId, HttpServletRequest request, Authentication authentication) {
        HttpSession currentSession = request.getSession(false);
        if (currentSession != null && currentSession.getId().equals(sessionId)) {
            throw new BadRequestException("不能踢出当前登录会话", "session", "不能踢出当前登录会话");
        }
        Session target = sessionRepository.findById(sessionId);
        if (target == null) {
            throw new BadRequestException("登录会话不存在或已失效", "session", "登录会话不存在或已失效");
        }
        ClientRequestContext context = sessionClientContext(target);
        AuthenticationLoginLog log = baseLog(AuthenticationLoginEventType.KICKOUT, context);
        log.setSessionId(sessionId);
        log.setUsername(targetUsername(target));
        log.setUserId(target.getAttribute(USER_ID_ATTRIBUTE));
        log.setDisplayName(target.getAttribute(DISPLAY_NAME_ATTRIBUTE));
        log.setOperatorUserId(currentUserId(authentication));
        log.setOperatorUsername(authentication.getName());
        loginLogRepository.insert(log);
        sessionRepository.deleteById(sessionId);
    }

    @Transactional(readOnly = true)
    public LoginSessionResponse currentLoginSession(
            HttpServletRequest request, Authentication authentication) {
        HttpSession httpSession = request.getSession(false);
        String sessionId = httpSession == null ? "" : httpSession.getId();
        Session session = sessionId.isEmpty() ? null : sessionRepository.findById(sessionId);
        ClientRequestContext context =
                session == null
                        ? contextFromSessionOrRequest(request)
                        : sessionClientContext(session);
        LocalDateTime creationTime =
                session == null
                        ? httpSessionTime(
                                httpSession == null ? null : httpSession.getCreationTime())
                        : dateTime(session.getCreationTime().toEpochMilli());
        LocalDateTime lastAccessTime =
                session == null
                        ? httpSessionTime(
                                httpSession == null ? null : httpSession.getLastAccessedTime())
                        : dateTime(session.getLastAccessedTime().toEpochMilli());
        LocalDateTime expiresAt =
                session == null
                        ? httpSessionExpiresAt(httpSession)
                        : dateTime(
                                session.getLastAccessedTime()
                                        .plus(session.getMaxInactiveInterval())
                                        .toEpochMilli());
        return new LoginSessionResponse(
                sessionId,
                currentUserId(authentication),
                authentication.getName(),
                displayName(authentication),
                roles(authentication),
                creationTime,
                lastAccessTime,
                expiresAt,
                true,
                context.client(),
                new RequestContextResponse(
                        context.remoteAddress(),
                        context.host(),
                        context.forwarded(),
                        context.xForwardedFor(),
                        context.xRealIp()));
    }

    @Transactional(readOnly = true)
    public CursorPageResponse<LoginSessionResponse> listLoginSessions(
            CursorPageRequest pageRequest, HttpServletRequest request) {
        long now = Instant.now().toEpochMilli();
        CursoredPage<SpringSessionRecord> page =
                springSessionRepository.find(
                        _SpringSessionRecord.expiryTime.greaterThan(now),
                        pageRequest.pageRequest(),
                        Order.by(
                                _SpringSessionRecord.lastAccessTime.desc(),
                                _SpringSessionRecord.sessionId.desc()));
        String currentSessionId =
                request.getSession(false) == null ? "" : request.getSession(false).getId();
        return CursorPageResponse.from(
                page, pageRequest, row -> toLoginSessionResponse(row, currentSessionId));
    }

    @Transactional(readOnly = true)
    public CursorPageResponse<LoginLogResponse> listLoginLogs(
            @Nullable String eventType,
            @Nullable String username,
            @Nullable String keyword,
            @Nullable LocalDateTime occurredAfter,
            @Nullable LocalDateTime occurredBefore,
            CursorPageRequest pageRequest) {
        Restriction<AuthenticationLoginLog> restriction =
                loginLogRestriction(eventType, username, keyword, occurredAfter, occurredBefore);
        CursoredPage<AuthenticationLoginLog> page =
                loginLogRepository.find(restriction, pageRequest.pageRequest());
        return CursorPageResponse.from(page, pageRequest, this::toLoginLogResponse);
    }

    private Restriction<AuthenticationLoginLog> loginLogRestriction(
            @Nullable String eventType,
            @Nullable String username,
            @Nullable String keyword,
            @Nullable LocalDateTime occurredAfter,
            @Nullable LocalDateTime occurredBefore) {
        List<Restriction<AuthenticationLoginLog>> restrictions = new ArrayList<>();
        String normalizedEventType = StringUtils.trimToNull(eventType);
        if (normalizedEventType != null) {
            restrictions.add(_AuthenticationLoginLog.eventType.equalTo(normalizedEventType));
        }
        String normalizedUsername = StringUtils.trimToNull(username);
        if (normalizedUsername != null) {
            restrictions.add(_AuthenticationLoginLog.username.equalTo(normalizedUsername));
        }
        String normalizedKeyword = StringUtils.trimToNull(keyword);
        if (normalizedKeyword != null) {
            String loweredKeyword = normalizedKeyword.toLowerCase();
            restrictions.add(
                    Restrict.any(
                            _AuthenticationLoginLog.username.lower().contains(loweredKeyword),
                            _AuthenticationLoginLog.displayName.lower().contains(loweredKeyword),
                            _AuthenticationLoginLog.remoteAddress.lower().contains(loweredKeyword),
                            _AuthenticationLoginLog.xForwardedFor.lower().contains(loweredKeyword),
                            _AuthenticationLoginLog.userAgent.lower().contains(loweredKeyword),
                            _AuthenticationLoginLog.browserName.lower().contains(loweredKeyword),
                            _AuthenticationLoginLog.osName.lower().contains(loweredKeyword)));
        }
        if (occurredAfter != null) {
            restrictions.add(_AuthenticationLoginLog.occurredAt.greaterThanEqual(occurredAfter));
        }
        if (occurredBefore != null) {
            restrictions.add(_AuthenticationLoginLog.occurredAt.lessThan(occurredBefore));
        }
        return restrictions.isEmpty() ? Restrict.unrestricted() : Restrict.all(restrictions);
    }

    private LoginSessionResponse toLoginSessionResponse(
            SpringSessionRecord row, String currentSessionId) {
        Session session = sessionRepository.findById(row.getSessionId());
        ClientRequestContext context =
                session == null ? emptyContext() : sessionClientContext(session);
        Long userId = session == null ? null : session.getAttribute(USER_ID_ATTRIBUTE);
        String displayName = session == null ? "" : session.getAttribute(DISPLAY_NAME_ATTRIBUTE);
        List<String> roles = session == null ? List.of() : sessionRoles(session);
        return new LoginSessionResponse(
                row.getSessionId(),
                userId,
                StringUtils.defaultString(row.getPrincipalName()),
                StringUtils.defaultString(displayName),
                roles,
                dateTime(row.getCreationTime()),
                dateTime(row.getLastAccessTime()),
                dateTime(row.getExpiryTime()),
                row.getSessionId().equals(currentSessionId),
                context.client(),
                new RequestContextResponse(
                        context.remoteAddress(),
                        context.host(),
                        context.forwarded(),
                        context.xForwardedFor(),
                        context.xRealIp()));
    }

    private AuthenticationLoginLog baseLog(
            AuthenticationLoginEventType eventType, ClientRequestContext context) {
        AuthenticationLoginLog log = new AuthenticationLoginLog();
        log.setEventType(eventType.value());
        log.setRemoteAddress(context.remoteAddress());
        log.setHost(context.host());
        log.setForwarded(context.forwarded());
        log.setXForwardedFor(context.xForwardedFor());
        log.setXRealIp(context.xRealIp());
        log.setUserAgent(context.client().userAgent());
        log.setBrowserName(context.client().browserName());
        log.setBrowserVersion(context.client().browserVersion());
        log.setOsName(context.client().osName());
        log.setOsVersion(context.client().osVersion());
        log.setDeviceType(context.client().deviceType());
        return log;
    }

    private ClientRequestContext contextFromSessionOrRequest(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        Object value = session == null ? null : session.getAttribute(CLIENT_CONTEXT_ATTRIBUTE);
        return value instanceof ClientRequestContext context
                ? context
                : contextResolver.resolve(request);
    }

    private ClientRequestContext sessionClientContext(Session session) {
        ClientRequestContext context = session.getAttribute(CLIENT_CONTEXT_ATTRIBUTE);
        return context == null ? emptyContext() : context;
    }

    private ClientRequestContext emptyContext() {
        return new ClientRequestContext("", "", "", "", "", new ClientInfo("", "", "", "", "", ""));
    }

    private @Nullable Long currentUserId(@Nullable Authentication authentication) {
        if (authentication == null
                || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            return null;
        }
        return user.id();
    }

    private String displayName(@Nullable Authentication authentication) {
        if (authentication != null
                && authentication.getPrincipal() instanceof AuthenticatedUser user) {
            return user.displayName();
        }
        return authentication == null ? "" : authentication.getName();
    }

    private List<String> roles(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(authority -> Strings.CS.removeStart(authority, "ROLE_"))
                .toList();
    }

    @SuppressWarnings("unchecked")
    private List<String> sessionRoles(Session session) {
        Object value = session.getAttribute(ROLES_ATTRIBUTE);
        return value instanceof List<?> roles ? (List<String>) roles : List.of();
    }

    private String targetUsername(Session target) {
        SecurityContext securityContext = target.getAttribute("SPRING_SECURITY_CONTEXT");
        if (securityContext != null && securityContext.getAuthentication() != null) {
            return securityContext.getAuthentication().getName();
        }
        return "";
    }

    private LocalDateTime dateTime(long epochMillis) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault());
    }

    private @Nullable LocalDateTime httpSessionTime(@Nullable Long epochMillis) {
        return epochMillis == null ? null : dateTime(epochMillis);
    }

    private @Nullable LocalDateTime httpSessionExpiresAt(@Nullable HttpSession session) {
        if (session == null || session.getMaxInactiveInterval() < 0) {
            return null;
        }
        return dateTime(
                Instant.ofEpochMilli(session.getLastAccessedTime())
                        .plusSeconds(session.getMaxInactiveInterval())
                        .toEpochMilli());
    }

    private LoginLogResponse toLoginLogResponse(AuthenticationLoginLog log) {
        return new LoginLogResponse(
                log.getId(),
                log.getEventType(),
                log.getUserId(),
                StringUtils.defaultString(log.getUsername()),
                StringUtils.defaultString(log.getDisplayName()),
                StringUtils.defaultString(log.getSessionId()),
                log.getOperatorUserId(),
                StringUtils.defaultString(log.getOperatorUsername()),
                StringUtils.defaultString(log.getFailureReason()),
                new ClientInfo(
                        StringUtils.defaultString(log.getUserAgent()),
                        StringUtils.defaultString(log.getBrowserName()),
                        StringUtils.defaultString(log.getBrowserVersion()),
                        StringUtils.defaultString(log.getOsName()),
                        StringUtils.defaultString(log.getOsVersion()),
                        StringUtils.defaultString(log.getDeviceType())),
                new RequestContextResponse(
                        StringUtils.defaultString(log.getRemoteAddress()),
                        StringUtils.defaultString(log.getHost()),
                        StringUtils.defaultString(log.getForwarded()),
                        StringUtils.defaultString(log.getXForwardedFor()),
                        StringUtils.defaultString(log.getXRealIp())),
                log.getOccurredAt());
    }

    public record RequestContextResponse(
            String remoteAddress,
            String host,
            String forwarded,
            String xForwardedFor,
            String xRealIp) {}

    public record LoginSessionResponse(
            String sessionId,
            @Nullable Long userId,
            String username,
            String displayName,
            List<String> roles,
            @Nullable LocalDateTime creationTime,
            @Nullable LocalDateTime lastAccessTime,
            @Nullable LocalDateTime expiresAt,
            boolean current,
            ClientInfo client,
            RequestContextResponse request) {}

    public record LoginLogResponse(
            @Nullable Long id,
            String eventType,
            @Nullable Long userId,
            String username,
            String displayName,
            String sessionId,
            @Nullable Long operatorUserId,
            String operatorUsername,
            String failureReason,
            ClientInfo client,
            RequestContextResponse request,
            @Nullable LocalDateTime occurredAt) {}
}
