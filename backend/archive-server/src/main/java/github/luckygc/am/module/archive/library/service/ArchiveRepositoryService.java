package github.luckygc.am.module.archive.library.service;

import java.util.List;

import jakarta.data.Limit;

import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import github.luckygc.am.common.exception.BadRequestException;
import github.luckygc.am.common.security.AuthenticatedUsers;
import github.luckygc.am.module.archive.item.repository.ArchiveItemDataRepository;
import github.luckygc.am.module.archive.item.repository.ArchiveVolumeDataRepository;
import github.luckygc.am.module.archive.library.ArchiveRepository;
import github.luckygc.am.module.archive.library.ArchiveRepositoryRole;
import github.luckygc.am.module.archive.library.repository.ArchiveRepositoryChangeHistoryDataRepository;
import github.luckygc.am.module.archive.library.repository.ArchiveRepositoryDataRepository;
import github.luckygc.am.module.authorization.service.AuthorizationPermissionCode;
import github.luckygc.am.module.authorization.service.AuthorizationPermissionService;

@Service
public class ArchiveRepositoryService {

    private final ArchiveRepositoryDataRepository repository;
    private final ArchiveRepositoryChangeHistoryDataRepository historyRepository;
    private final ArchiveItemDataRepository itemRepository;
    private final ArchiveVolumeDataRepository volumeRepository;
    private final AuthorizationPermissionService permissionService;

    public ArchiveRepositoryService(
            ArchiveRepositoryDataRepository repository,
            ArchiveRepositoryChangeHistoryDataRepository historyRepository,
            ArchiveItemDataRepository itemRepository,
            ArchiveVolumeDataRepository volumeRepository,
            AuthorizationPermissionService permissionService) {
        this.repository = repository;
        this.historyRepository = historyRepository;
        this.itemRepository = itemRepository;
        this.volumeRepository = volumeRepository;
        this.permissionService = permissionService;
    }

    public List<ArchiveRepositoryResponse> list(@Nullable Boolean enabled) {
        List<ArchiveRepository> rows =
                enabled == null ? repository.list() : repository.list(enabled);
        return rows.stream().map(this::toResponse).toList();
    }

    @Transactional
    public ArchiveRepositoryResponse create(CreateArchiveRepositoryRequest request, Long userId) {
        requireManagePermission(userId);
        if (request == null) {
            throw new BadRequestException("请求体不能为空");
        }
        String code = required(request.repositoryCode(), "业务库编码不能为空");
        String name = required(request.repositoryName(), "业务库名称不能为空");
        if (request.repositoryRole() == null) {
            throw new BadRequestException("业务库角色不能为空");
        }
        ArchiveRepository entity = new ArchiveRepository();
        entity.setRepositoryCode(code);
        entity.setRepositoryName(name);
        entity.setRepositoryRole(request.repositoryRole());
        entity.setEnabled(request.enabled() == null || request.enabled());
        entity.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
        try {
            return toResponse(repository.insert(entity));
        } catch (DataIntegrityViolationException exception) {
            throw new BadRequestException("业务库编码已存在", "repositoryCode", "业务库编码已存在");
        }
    }

    @Transactional
    public ArchiveRepositoryResponse update(
            Long id, UpdateArchiveRepositoryRequest request, Long userId) {
        requireManagePermission(userId);
        ArchiveRepository entity = get(id);
        if (request == null) {
            throw new BadRequestException("请求体不能为空");
        }
        if (entity.isSystemFlag()) {
            assertSystemRepositoryUpdate(entity, request);
        }
        if (request.repositoryCode() != null) {
            entity.setRepositoryCode(required(request.repositoryCode(), "业务库编码不能为空"));
        }
        if (request.repositoryName() != null) {
            entity.setRepositoryName(required(request.repositoryName(), "业务库名称不能为空"));
        }
        if (request.repositoryRole() != null) {
            entity.setRepositoryRole(request.repositoryRole());
        }
        if (request.enabled() != null) {
            entity.setEnabled(request.enabled());
        }
        if (request.sortOrder() != null) {
            entity.setSortOrder(request.sortOrder());
        }
        try {
            return toResponse(repository.update(entity));
        } catch (DataIntegrityViolationException exception) {
            throw new BadRequestException("业务库编码已存在", "repositoryCode", "业务库编码已存在");
        }
    }

    @Transactional
    public void delete(Long id, Long userId) {
        requireManagePermission(userId);
        ArchiveRepository entity = get(id);
        if (entity.isSystemFlag()) {
            throw new BadRequestException("系统内置业务库不能删除");
        }
        if (isReferenced(id)) {
            throw new BadRequestException("业务库仍在使用，不能删除");
        }
        repository.delete(entity);
    }

    public ArchiveRepository getEnabled(Long id) {
        ArchiveRepository entity = get(id);
        if (!entity.isEnabled()) {
            throw new BadRequestException("目标业务库已禁用");
        }
        return entity;
    }

    private ArchiveRepository get(Long id) {
        if (id == null || id <= 0) {
            throw new BadRequestException("业务库 ID 不合法");
        }
        return repository
                .findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "业务库不存在"));
    }

    private boolean isReferenced(Long id) {
        Limit one = Limit.of(1);
        return !itemRepository.findByRepositoryId(id, one).isEmpty()
                || !volumeRepository.findByRepositoryId(id, one).isEmpty()
                || !historyRepository.findByFromRepositoryId(id, one).isEmpty()
                || !historyRepository.findByToRepositoryId(id, one).isEmpty();
    }

    private void assertSystemRepositoryUpdate(
            ArchiveRepository entity, UpdateArchiveRepositoryRequest request) {
        if ((request.repositoryCode() != null
                        && !entity.getRepositoryCode()
                                .equals(StringUtils.trim(request.repositoryCode())))
                || (request.repositoryRole() != null
                        && request.repositoryRole() != entity.getRepositoryRole())
                || Boolean.FALSE.equals(request.enabled())) {
            throw new BadRequestException("系统内置业务库的编码、角色和启用状态不能修改");
        }
    }

    private String required(@Nullable String value, String message) {
        if (StringUtils.isBlank(value)) {
            throw new BadRequestException(message);
        }
        return value.trim();
    }

    private void requireManagePermission(Long userId) {
        userId = AuthenticatedUsers.requireResolvedUserId(userId);
        if (!permissionService.hasPermission(
                userId, AuthorizationPermissionCode.ARCHIVE_METADATA_MANAGE.code())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "权限不足");
        }
    }

    private ArchiveRepositoryResponse toResponse(ArchiveRepository entity) {
        return new ArchiveRepositoryResponse(
                entity.getId(),
                entity.getRepositoryCode(),
                entity.getRepositoryName(),
                entity.getRepositoryRole(),
                entity.isEnabled(),
                entity.getSortOrder(),
                entity.isSystemFlag(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    public record CreateArchiveRepositoryRequest(
            @Nullable String repositoryCode,
            @Nullable String repositoryName,
            @Nullable ArchiveRepositoryRole repositoryRole,
            @Nullable Boolean enabled,
            @Nullable Integer sortOrder) {}

    public record UpdateArchiveRepositoryRequest(
            @Nullable String repositoryCode,
            @Nullable String repositoryName,
            @Nullable ArchiveRepositoryRole repositoryRole,
            @Nullable Boolean enabled,
            @Nullable Integer sortOrder) {}

    public record ArchiveRepositoryResponse(
            Long id,
            String repositoryCode,
            String repositoryName,
            ArchiveRepositoryRole repositoryRole,
            boolean enabled,
            int sortOrder,
            boolean systemFlag,
            java.time.LocalDateTime createdAt,
            java.time.LocalDateTime updatedAt) {}
}
