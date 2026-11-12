package github.luckygc.am.module.authentication.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
import github.luckygc.am.module.authentication.AuthenticationUser;
import github.luckygc.am.module.authentication.ServiceAccount;
import github.luckygc.am.module.authentication.ServiceAccountStatus;
import github.luckygc.am.module.authentication.repository.AuthenticationUserDataRepository;
import github.luckygc.am.module.authentication.repository.ServiceAccountDataRepository;
import github.luckygc.am.module.authorization.service.AuthorizationPermissionCode;
import github.luckygc.am.module.authorization.service.AuthorizationPermissionService;
import github.luckygc.am.module.authorization.service.AuthorizationServiceAccountRoleService;

@Service
public class ServiceAccountManagementService {

    private final ServiceAccountDataRepository serviceAccountRepository;
    private final AuthenticationUserDataRepository userRepository;
    private final AuthorizationPermissionService permissionService;
    private final AuthorizationServiceAccountRoleService roleService;
    private final AccessTokenUsageService tokenUsageService;
    private final Clock clock;

    public ServiceAccountManagementService(
            ServiceAccountDataRepository serviceAccountRepository,
            AuthenticationUserDataRepository userRepository,
            AuthorizationPermissionService permissionService,
            AuthorizationServiceAccountRoleService roleService,
            AccessTokenUsageService tokenUsageService,
            Clock clock) {
        this.serviceAccountRepository = serviceAccountRepository;
        this.userRepository = userRepository;
        this.permissionService = permissionService;
        this.roleService = roleService;
        this.tokenUsageService = tokenUsageService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public CursorPageResponse<ServiceAccountResponse> list(
            @Nullable ServiceAccountStatus status, PageRequest pageRequest, Long operatorUserId) {
        requireManage(operatorUserId);
        CursoredPage<ServiceAccount> page =
                status == null
                        ? serviceAccountRepository.list(pageRequest)
                        : serviceAccountRepository.findByStatus(status, pageRequest);
        Map<Long, String> ownerNames = ownerNames(page.content());
        Map<Long, List<Long>> roleIds =
                roleService.listRoleIdsByServiceAccount(
                        page.content().stream().map(ServiceAccount::getId).toList());
        return CursorPageResponse.from(
                page,
                pageRequest,
                account ->
                        toResponse(
                                account,
                                ownerNames,
                                roleIds.getOrDefault(account.getId(), List.of())));
    }

    @Transactional(readOnly = true)
    public ServiceAccountResponse get(Long id, Long operatorUserId) {
        requireManage(operatorUserId);
        return loadResponse(id);
    }

    @Transactional
    public ServiceAccountResponse create(CreateServiceAccountRequest request, Long operatorUserId) {
        requireManage(operatorUserId);
        String name = requireName(request.name());
        requireOwner(request.ownerUserId());
        if (serviceAccountRepository.findByName(name) != null) {
            throw nameExists(name);
        }
        ServiceAccount account = new ServiceAccount();
        account.setName(name);
        account.setDescription(normalizeDescription(request.description()));
        account.setOwnerUserId(request.ownerUserId());
        account.setStatus(ServiceAccountStatus.ACTIVE);
        try {
            account = serviceAccountRepository.insert(account);
        } catch (DataIntegrityViolationException exception) {
            throw nameExists(name);
        }
        roleService.replaceRoles(
                account.getId(), request.roleIds() == null ? List.of() : request.roleIds());
        return loadResponse(account.getId());
    }

    @Transactional
    public ServiceAccountResponse update(
            Long id, UpdateServiceAccountRequest request, Long operatorUserId) {
        requireManage(operatorUserId);
        ServiceAccount account = requireAccount(id);
        if (request.name() != null) {
            String name = requireName(request.name());
            ServiceAccount existing = serviceAccountRepository.findByName(name);
            if (existing != null && !existing.getId().equals(account.getId())) {
                throw nameExists(name);
            }
            account.setName(name);
        }
        if (request.descriptionChanged()) {
            account.setDescription(normalizeDescription(request.description()));
        }
        if (request.ownerUserId() != null) {
            requireOwner(request.ownerUserId());
            account.setOwnerUserId(request.ownerUserId());
        }
        if (request.status() != null) {
            account.setStatus(request.status());
        }
        serviceAccountRepository.update(account);
        return loadResponse(account.getId());
    }

    @Transactional
    public ServiceAccountResponse replaceRoles(Long id, List<Long> roleIds, Long operatorUserId) {
        requireManage(operatorUserId);
        ServiceAccount account = requireAccount(id);
        roleService.replaceRoles(account.getId(), roleIds == null ? List.of() : roleIds);
        return loadResponse(account.getId());
    }

    @Transactional
    public void delete(Long id, Long operatorUserId) {
        requireManage(operatorUserId);
        ServiceAccount account = requireAccount(id);
        account.setDeletedAt(LocalDateTime.now(clock));
        serviceAccountRepository.update(account);
        serviceAccountRepository.delete(account);
        roleService.clearRoles(account.getId());
        tokenUsageService.revokeActiveForServiceAccount(account.getId());
    }

    private ServiceAccountResponse loadResponse(Long id) {
        ServiceAccount account = requireAccount(id);
        return toResponse(
                account,
                ownerNames(List.of(account)),
                roleService.listAssignedRoles(account.getId()).stream()
                        .map(role -> role.getId())
                        .toList());
    }

    private void requireManage(Long operatorUserId) {
        permissionService.requirePermission(
                operatorUserId, AuthorizationPermissionCode.AUTHENTICATION_SERVICE_ACCOUNT_MANAGE);
    }

    private ServiceAccount requireAccount(Long id) {
        ServiceAccount account =
                serviceAccountRepository
                        .findById(id)
                        .orElseThrow(ServiceAccountManagementService::notFound);
        return account;
    }

    private void requireOwner(Long ownerUserId) {
        if (ownerUserId == null || ownerUserId <= 0) {
            throw new BadRequestException("负责人不存在", "ownerUserId", "负责人不存在");
        }
        userRepository
                .findById(ownerUserId)
                .orElseThrow(() -> new BadRequestException("负责人不存在", "ownerUserId", "负责人不存在"));
    }

    private String requireName(String name) {
        String normalized = StringUtils.trimToNull(name);
        if (normalized == null || normalized.length() > 100) {
            throw new BadRequestException("服务账号名称不合法", "name", "名称不能为空且不超过 100 个字符");
        }
        return normalized;
    }

    private @Nullable String normalizeDescription(@Nullable String description) {
        String normalized = StringUtils.trimToNull(description);
        if (normalized != null && normalized.length() > 500) {
            throw new BadRequestException("说明不能超过 500 个字符", "description", "说明不能超过 500 个字符");
        }
        return normalized;
    }

    private Map<Long, String> ownerNames(List<ServiceAccount> accounts) {
        List<Long> ownerIds =
                accounts.stream().map(ServiceAccount::getOwnerUserId).distinct().toList();
        if (ownerIds.isEmpty()) {
            return Map.of();
        }
        return userRepository.findByIdIn(ownerIds).stream()
                .collect(
                        Collectors.toMap(
                                AuthenticationUser::getId, AuthenticationUser::getDisplayName));
    }

    private ServiceAccountResponse toResponse(
            ServiceAccount account, Map<Long, String> ownerNames, List<Long> roleIds) {
        return new ServiceAccountResponse(
                account.getId(),
                account.getName(),
                account.getDescription(),
                account.getOwnerUserId(),
                ownerNames.getOrDefault(account.getOwnerUserId(), ""),
                account.getStatus().name(),
                roleIds,
                account.getCreatedAt() == null ? "" : account.getCreatedAt().toString());
    }

    private static BadRequestException nameExists(String name) {
        return new BadRequestException("服务账号名称已存在", "name", "名称 " + name + " 已存在");
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "服务账号不存在");
    }

    public record CreateServiceAccountRequest(
            String name,
            @Nullable String description,
            Long ownerUserId,
            @Nullable List<Long> roleIds) {}

    public record UpdateServiceAccountRequest(
            @Nullable String name,
            boolean descriptionChanged,
            @Nullable String description,
            @Nullable Long ownerUserId,
            @Nullable ServiceAccountStatus status) {}

    public record ReplaceServiceAccountRolesRequest(@Nullable List<Long> roleIds) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ServiceAccountResponse(
            Long id,
            String name,
            @Nullable String description,
            Long ownerUserId,
            String ownerDisplayName,
            String status,
            List<Long> roleIds,
            String createdAt) {}
}
