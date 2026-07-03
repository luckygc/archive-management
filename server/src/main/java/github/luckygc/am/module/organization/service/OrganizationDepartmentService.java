package github.luckygc.am.module.organization.service;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import github.luckygc.am.common.exception.BadRequestException;
import github.luckygc.am.module.organization.OrganizationDepartment;
import github.luckygc.am.module.organization.repository.OrganizationDepartmentDataRepository;

@Service
public class OrganizationDepartmentService {

    private final OrganizationDepartmentDataRepository departmentRepository;

    public OrganizationDepartmentService(
            OrganizationDepartmentDataRepository departmentRepository) {
        this.departmentRepository = departmentRepository;
    }

    @Transactional(readOnly = true)
    public List<OrganizationDepartmentResponse> listDepartments(@Nullable Boolean enabled) {
        List<OrganizationDepartment> departments =
                enabled == null ? departmentRepository.list() : departmentRepository.list(enabled);
        return departments.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public OrganizationDepartmentResponse getDepartment(Long id) {
        return toResponse(loadDepartment(id));
    }

    @Transactional
    public OrganizationDepartmentResponse createDepartment(
            CreateOrganizationDepartmentRequest request) {
        String code = requireText(request.departmentCode(), "departmentCode", "部门编码不能为空");
        String name = requireText(request.departmentName(), "departmentName", "部门名称不能为空");
        if (departmentRepository.findByDepartmentCode(code) != null) {
            throw new BadRequestException("部门编码已存在", "departmentCode", "部门编码已存在");
        }
        validateParentExists(request.parentId());
        OrganizationDepartment department = new OrganizationDepartment();
        department.setDepartmentCode(code);
        department.setDepartmentName(name);
        department.setParentId(request.parentId());
        department.setEnabled(request.enabled() == null || request.enabled());
        department.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
        return toResponse(departmentRepository.insert(department));
    }

    @Transactional
    public OrganizationDepartmentResponse updateDepartment(
            Long id, UpdateOrganizationDepartmentRequest request) {
        OrganizationDepartment department = loadDepartment(id);
        if (request.departmentCode() != null) {
            String code = requireText(request.departmentCode(), "departmentCode", "部门编码不能为空");
            OrganizationDepartment existing = departmentRepository.findByDepartmentCode(code);
            if (existing != null && !existing.getId().equals(id)) {
                throw new BadRequestException("部门编码已存在", "departmentCode", "部门编码已存在");
            }
            department.setDepartmentCode(code);
        }
        if (request.departmentName() != null) {
            department.setDepartmentName(
                    requireText(request.departmentName(), "departmentName", "部门名称不能为空"));
        }
        if (request.parentId() != null) {
            validateParent(id, request.parentId());
            department.setParentId(request.parentId());
        }
        if (request.enabled() != null) {
            department.setEnabled(request.enabled());
        }
        if (request.sortOrder() != null) {
            department.setSortOrder(request.sortOrder());
        }
        return toResponse(departmentRepository.update(department));
    }

    @Transactional(readOnly = true)
    public OrganizationDepartmentResponse requireEnabledDepartment(Long id) {
        OrganizationDepartment department = loadDepartment(id);
        if (!department.isEnabled()) {
            throw new BadRequestException("部门已停用", "departmentId", "部门已停用");
        }
        return toResponse(department);
    }

    private OrganizationDepartment loadDepartment(Long id) {
        if (id == null || id <= 0) {
            throw new BadRequestException("部门不合法", "departmentId", "部门不合法");
        }
        return departmentRepository
                .findById(id)
                .orElseThrow(() -> new BadRequestException("部门不存在", "departmentId", "部门不存在"));
    }

    private void validateParentExists(@Nullable Long parentId) {
        if (parentId != null) {
            loadDepartment(parentId);
        }
    }

    private void validateParent(Long id, Long parentId) {
        if (id.equals(parentId)) {
            throw new BadRequestException("父部门不能是当前部门", "parentId", "父部门不能是当前部门");
        }
        loadDepartment(parentId);
        Set<Long> descendants = descendantIds(id);
        if (descendants.contains(parentId)) {
            throw new BadRequestException("父部门不能是当前部门的下级部门", "parentId", "父部门不能是当前部门的下级部门");
        }
    }

    private Set<Long> descendantIds(Long id) {
        Set<Long> descendants = new HashSet<>();
        boolean changed;
        do {
            changed = false;
            for (OrganizationDepartment department : departmentRepository.list()) {
                Long parentId = department.getParentId();
                if (parentId != null
                        && (parentId.equals(id) || descendants.contains(parentId))
                        && descendants.add(department.getId())) {
                    changed = true;
                }
            }
        } while (changed);
        return descendants;
    }

    private String requireText(@Nullable String value, String field, String message) {
        String normalized = StringUtils.trimToNull(value);
        if (normalized == null) {
            throw new BadRequestException(message, field, message);
        }
        return normalized;
    }

    private OrganizationDepartmentResponse toResponse(OrganizationDepartment department) {
        return new OrganizationDepartmentResponse(
                department.getId(),
                department.getDepartmentCode(),
                department.getDepartmentName(),
                department.getParentId(),
                department.isEnabled(),
                department.getSortOrder(),
                department.getCreatedAt(),
                department.getUpdatedAt());
    }

    public record CreateOrganizationDepartmentRequest(
            @Nullable String departmentCode,
            @Nullable String departmentName,
            @Nullable Long parentId,
            @Nullable Boolean enabled,
            @Nullable Integer sortOrder) {}

    public record UpdateOrganizationDepartmentRequest(
            @Nullable String departmentCode,
            @Nullable String departmentName,
            @Nullable Long parentId,
            @Nullable Boolean enabled,
            @Nullable Integer sortOrder) {}

    public record OrganizationDepartmentResponse(
            Long id,
            String departmentCode,
            String departmentName,
            @Nullable Long parentId,
            boolean enabled,
            int sortOrder,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {}
}
