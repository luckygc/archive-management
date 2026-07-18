package github.luckygc.am.module.storage.service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;

import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import github.luckygc.am.module.storage.FileLink;
import github.luckygc.am.module.storage.FileLinkTargetType;
import github.luckygc.am.module.storage.repository.FileLinkDataRepository;

@Service
public class FileLinkService {

    private static final int MAX_CODE_GENERATION_ATTEMPTS = 3;

    private final FileLinkDataRepository fileLinkRepository;
    private final FileLinkCodeGenerator codeGenerator;
    private final Clock clock;

    public FileLinkService(
            FileLinkDataRepository fileLinkRepository,
            FileLinkCodeGenerator codeGenerator,
            Clock clock) {
        this.fileLinkRepository = fileLinkRepository;
        this.codeGenerator = codeGenerator;
        this.clock = clock;
    }

    @Transactional
    public FileLinkCreated createUserLink(
            FileLinkTargetType targetType,
            @Nullable Long targetParentId,
            Long targetId,
            Duration ttl,
            Long userId) {
        userId = requireUserId(userId);
        LocalDateTime expiresAt = LocalDateTime.now(clock).plus(ttl);
        FileLink link = createLink(targetType, targetParentId, targetId, userId, userId, expiresAt);
        return new FileLinkCreated(link.getCode(), link.getExpiresAt());
    }

    @Transactional
    public FileLinkCreated createUserLinkUntil(
            FileLinkTargetType targetType,
            @Nullable Long targetParentId,
            Long targetId,
            LocalDateTime expiresAt,
            Long userId) {
        userId = requireUserId(userId);
        FileLink link = createLink(targetType, targetParentId, targetId, userId, userId, expiresAt);
        return new FileLinkCreated(link.getCode(), link.getExpiresAt());
    }

    @Transactional
    public FileLinkCreated createPublicLink(
            FileLinkTargetType targetType,
            @Nullable Long targetParentId,
            Long targetId,
            Duration ttl,
            Long createdBy) {
        LocalDateTime expiresAt = LocalDateTime.now(clock).plus(ttl);
        FileLink link =
                createLink(targetType, targetParentId, targetId, null, createdBy, expiresAt);
        return new FileLinkCreated(link.getCode(), link.getExpiresAt());
    }

    private FileLink createLink(
            FileLinkTargetType targetType,
            @Nullable Long targetParentId,
            Long targetId,
            @Nullable Long allowedUserId,
            @Nullable Long createdBy,
            LocalDateTime expiresAt) {
        for (int attempt = 1; attempt <= MAX_CODE_GENERATION_ATTEMPTS; attempt++) {
            FileLink link = new FileLink();
            link.setCode(codeGenerator.generate());
            link.setTargetType(targetType);
            link.setTargetParentId(targetParentId);
            link.setTargetId(targetId);
            link.setAllowedUserId(allowedUserId);
            link.setExpiresAt(expiresAt);
            link.setCreatedBy(createdBy);
            try {
                return fileLinkRepository.insert(link);
            } catch (DataIntegrityViolationException exception) {
                if (attempt == MAX_CODE_GENERATION_ATTEMPTS) {
                    throw exception;
                }
            }
        }
        throw new IllegalStateException("文件短链短码生成失败");
    }

    @Transactional(readOnly = true)
    public FileLinkTarget resolveInternal(String code, Long userId) {
        userId = requireUserId(userId);
        FileLink link = activeLink(code);
        Long allowedUserId = link.getAllowedUserId();
        if (allowedUserId != null && !allowedUserId.equals(userId)) {
            throw notFound();
        }
        return toTarget(link);
    }

    @Transactional(readOnly = true)
    public FileLinkTarget resolvePublic(String code) {
        FileLink link = activeLink(code);
        if (link.getAllowedUserId() != null) {
            throw notFound();
        }
        return toTarget(link);
    }

    private FileLink activeLink(String code) {
        FileLink link = fileLinkRepository.findByCode(code).orElseThrow(this::notFound);
        LocalDateTime now = LocalDateTime.now(clock);
        if (link.getRevokedAt() != null || !link.getExpiresAt().isAfter(now)) {
            throw notFound();
        }
        return link;
    }

    private FileLinkTarget toTarget(FileLink link) {
        return new FileLinkTarget(
                link.getTargetType(), link.getTargetParentId(), link.getTargetId());
    }

    private ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "文件短链不存在");
    }

    private Long requireUserId(@Nullable Long userId) {
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录");
        }
        return userId;
    }

    public record FileLinkCreated(String code, LocalDateTime expiresAt) {}

    public record FileLinkTarget(
            FileLinkTargetType targetType, @Nullable Long targetParentId, Long targetId) {}
}
