package github.luckygc.am.module.authentication.service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.List;
import java.util.OptionalLong;

import jakarta.servlet.http.HttpServletRequest;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import github.luckygc.am.common.exception.BadRequestException;
import github.luckygc.am.module.authentication.AuthenticationLoginEventType;
import github.luckygc.am.module.authentication.AuthenticationTotpCredential;
import github.luckygc.am.module.authentication.AuthenticationTotpEnrollment;
import github.luckygc.am.module.authentication.AuthenticationUser;
import github.luckygc.am.module.authentication.repository.AuthenticationTotpCredentialDataRepository;
import github.luckygc.am.module.authentication.repository.AuthenticationTotpEnrollmentDataRepository;
import github.luckygc.am.module.authentication.repository.AuthenticationTotpLoginChallengeDataRepository;
import github.luckygc.am.module.authentication.repository.AuthenticationUserDataRepository;
import github.luckygc.am.module.authorization.service.AuthorizationPermissionCode;
import github.luckygc.am.module.authorization.service.AuthorizationPermissionService;

@Service
public class TotpCredentialService {

    private static final String ISSUER = "Archive Management";
    private static final Duration ENROLLMENT_TTL = Duration.ofMinutes(10);

    private final AuthenticationTotpCredentialDataRepository credentialRepository;
    private final AuthenticationTotpEnrollmentDataRepository enrollmentRepository;
    private final AuthenticationTotpLoginChallengeDataRepository challengeRepository;
    private final AuthenticationUserDataRepository userRepository;
    private final AuthorizationPermissionService permissionService;
    private final AuthenticationAuditService auditService;
    private final LoginFailureLimitService failureLimitService;
    private final TotpCodeService codeService;
    private final PasswordEncoder passwordEncoder;
    private final TextEncryptor textEncryptor;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    public TotpCredentialService(
            AuthenticationTotpCredentialDataRepository credentialRepository,
            AuthenticationTotpEnrollmentDataRepository enrollmentRepository,
            AuthenticationTotpLoginChallengeDataRepository challengeRepository,
            AuthenticationUserDataRepository userRepository,
            AuthorizationPermissionService permissionService,
            AuthenticationAuditService auditService,
            LoginFailureLimitService failureLimitService,
            TotpCodeService codeService,
            PasswordEncoder passwordEncoder,
            @Qualifier("totpTextEncryptor") TextEncryptor textEncryptor,
            Clock clock) {
        this.credentialRepository = credentialRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.challengeRepository = challengeRepository;
        this.userRepository = userRepository;
        this.permissionService = permissionService;
        this.auditService = auditService;
        this.failureLimitService = failureLimitService;
        this.codeService = codeService;
        this.passwordEncoder = passwordEncoder;
        this.textEncryptor = textEncryptor;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public boolean isEnabled(Long userId) {
        return credentialRepository.findById(userId).isPresent();
    }

    @Transactional(rollbackFor = Throwable.class)
    public TotpEnrollmentResponse prepareEnrollment(Long userId) {
        AuthenticationUser user = requireEnabledUser(userId);
        if (credentialRepository.findById(userId).isPresent()) {
            throw badRequest("TOTP 已启用", "TOTP_ALREADY_ENABLED", "TOTP_ALREADY_ENABLED");
        }
        String secret = codeService.generateSecret();
        Instant expiresAt = clock.instant().plus(ENROLLMENT_TTL);
        String encryptedSecret;
        try {
            encryptedSecret = textEncryptor.encrypt(secret);
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "TOTP 服务未配置");
        }
        byte[] tokenBytes = new byte[32];
        secureRandom.nextBytes(tokenBytes);
        String enrollmentToken = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        enrollmentRepository.deleteByUserId(userId);
        AuthenticationTotpEnrollment enrollment = new AuthenticationTotpEnrollment();
        enrollment.setTokenKey(tokenKey(enrollmentToken));
        enrollment.setUserId(userId);
        enrollment.setEncryptedSecret(encryptedSecret);
        enrollment.setExpiresAt(localDateTime(expiresAt));
        enrollmentRepository.insert(enrollment);
        return new TotpEnrollmentResponse(
                enrollmentToken,
                secret,
                codeService.provisioningUri(ISSUER, user.getUsername(), secret),
                expiresAt);
    }

    @Transactional(rollbackFor = Throwable.class)
    public void createCredential(
            CreateTotpCredentialRequest request,
            Long userId,
            String operatorUsername,
            HttpServletRequest httpRequest) {
        AuthenticationUser user = requireEnabledUser(userId);
        if (credentialRepository.findById(userId).isPresent()) {
            throw badRequest("TOTP 已启用", "TOTP_ALREADY_ENABLED", "TOTP_ALREADY_ENABLED");
        }
        failureLimitService.assertLoginAllowed(user.getUsername());
        AuthenticationTotpEnrollment enrollment =
                enrollmentRepository
                        .findById(tokenKey(request.enrollmentToken()))
                        .filter(row -> row.getUserId().equals(userId))
                        .filter(row -> row.getExpiresAt().isAfter(localDateTime(clock.instant())))
                        .orElse(null);
        if (enrollment == null) {
            enrollmentVerificationFailed(user.getUsername());
        }
        requireCurrentPassword(user, request.currentPassword());
        String secret;
        try {
            secret = textEncryptor.decrypt(enrollment.getEncryptedSecret());
        } catch (IllegalStateException ex) {
            failureLimitService.recordFailure(user.getUsername());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "TOTP 服务不可用");
        }
        OptionalLong step = codeService.findMatchingStep(secret, request.code(), clock.instant());
        if (step.isEmpty()) {
            credentialVerificationFailed(user.getUsername());
        }
        if (enrollmentRepository.consume(
                        enrollment.getTokenKey(), userId, localDateTime(clock.instant()))
                != 1) {
            enrollmentVerificationFailed(user.getUsername());
        }

        AuthenticationTotpCredential credential = new AuthenticationTotpCredential();
        credential.setUserId(userId);
        credential.setEncryptedSecret(enrollment.getEncryptedSecret());
        credential.setLastAcceptedStep(step.orElseThrow());
        credentialRepository.insert(credential);
        auditService.recordTotpCredentialEvent(
                httpRequest,
                AuthenticationLoginEventType.TOTP_ENABLED,
                user,
                userId,
                operatorUsername);
    }

    @Transactional(rollbackFor = Throwable.class)
    public void disableCredential(
            DisableTotpCredentialRequest request,
            Long userId,
            String operatorUsername,
            HttpServletRequest httpRequest) {
        AuthenticationUser user = requireEnabledUser(userId);
        failureLimitService.assertLoginAllowed(user.getUsername());
        AuthenticationTotpCredential credential =
                credentialRepository
                        .findById(userId)
                        .orElseThrow(
                                () ->
                                        badRequest(
                                                "TOTP 尚未启用",
                                                "TOTP_NOT_ENABLED",
                                                "TOTP_NOT_ENABLED"));
        requireCurrentPassword(user, request.currentPassword());
        if (!verifyAndAdvanceInternal(credential, request.code())) {
            credentialVerificationFailed(user.getUsername());
        }
        credentialRepository.deleteByUserId(userId);
        auditService.recordTotpCredentialEvent(
                httpRequest,
                AuthenticationLoginEventType.TOTP_DISABLED,
                user,
                userId,
                operatorUsername);
        clearTransientStateInternal(userId);
        failureLimitService.clear(user.getUsername());
    }

    @Transactional(rollbackFor = Throwable.class)
    public void resetCredential(
            Long targetUserId,
            Long operatorUserId,
            String operatorUsername,
            HttpServletRequest httpRequest) {
        permissionService.requirePermission(
                operatorUserId, AuthorizationPermissionCode.AUTHENTICATION_USER_MANAGE);
        AuthenticationUser target = requireUser(targetUserId);
        credentialRepository.deleteByUserId(targetUserId);
        clearTransientStateInternal(targetUserId);
        auditService.recordTotpCredentialEvent(
                httpRequest,
                AuthenticationLoginEventType.TOTP_RESET,
                target,
                operatorUserId,
                operatorUsername);
    }

    @Transactional(rollbackFor = Throwable.class)
    public boolean verifyAndAdvance(Long userId, String code) {
        AuthenticationTotpCredential credential =
                credentialRepository.findById(userId).orElse(null);
        return credential != null && verifyAndAdvanceInternal(credential, code);
    }

    @Transactional(rollbackFor = Throwable.class)
    public void clearTransientState(Long userId) {
        clearTransientStateInternal(userId);
    }

    private boolean verifyAndAdvanceInternal(AuthenticationTotpCredential credential, String code) {
        String secret = textEncryptor.decrypt(credential.getEncryptedSecret());
        OptionalLong matchingStep = codeService.findMatchingStep(secret, code, clock.instant());
        if (matchingStep.isEmpty()
                || matchingStep.getAsLong() <= credential.getLastAcceptedStep()) {
            return false;
        }
        return credentialRepository.advanceAcceptedStep(
                        credential.getUserId(),
                        matchingStep.getAsLong(),
                        localDateTime(clock.instant()))
                == 1;
    }

    private void clearTransientStateInternal(Long userId) {
        enrollmentRepository.deleteByUserId(userId);
        challengeRepository.deleteByUserId(userId);
    }

    private void requireCurrentPassword(AuthenticationUser user, String currentPassword) {
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            credentialVerificationFailed(user.getUsername());
        }
    }

    private void credentialVerificationFailed(String username) {
        failureLimitService.recordFailure(username);
        throw badRequest(
                "当前密码或验证码错误",
                "TOTP_CREDENTIAL_VERIFICATION_FAILED",
                "TOTP_CREDENTIAL_VERIFICATION_FAILED");
    }

    private void enrollmentVerificationFailed(String username) {
        failureLimitService.recordFailure(username);
        throw enrollmentInvalid();
    }

    private AuthenticationUser requireEnabledUser(Long userId) {
        AuthenticationUser user = requireUser(userId);
        if (!user.isEnabled()) {
            throw badRequest("用户已停用", "USER_DISABLED", "USER_DISABLED");
        }
        return user;
    }

    private AuthenticationUser requireUser(Long userId) {
        return userRepository
                .findById(userId)
                .orElseThrow(() -> new BadRequestException("用户不存在", "id", "用户不存在"));
    }

    private BadRequestException enrollmentInvalid() {
        return badRequest(
                "TOTP enrollment 无效或已过期", "TOTP_ENROLLMENT_INVALID", "TOTP_ENROLLMENT_INVALID");
    }

    private BadRequestException badRequest(String message, String code, String reason) {
        return new BadRequestException(message, List.of(), code, reason);
    }

    private String tokenKey(String token) {
        return DigestUtils.sha256Hex(StringUtils.defaultString(token));
    }

    private LocalDateTime localDateTime(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
    }

    public record TotpEnrollmentResponse(
            String enrollmentToken, String manualKey, String otpauthUri, Instant expiresAt) {}

    public record CreateTotpCredentialRequest(
            String enrollmentToken, String currentPassword, String code) {}

    public record DisableTotpCredentialRequest(String currentPassword, String code) {}
}
