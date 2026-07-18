package github.luckygc.am.module.organization.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import github.luckygc.am.common.exception.BadRequestException;
import github.luckygc.am.module.organization.OrganizationDepartment;
import github.luckygc.am.module.organization.repository.OrganizationDepartmentDataRepository;
import github.luckygc.am.module.organization.service.OrganizationDepartmentService.CreateOrganizationDepartmentRequest;
import github.luckygc.am.module.organization.service.OrganizationDepartmentService.UpdateOrganizationDepartmentRequest;

@DisplayName("组织架构部门服务")
class OrganizationDepartmentServiceTests {

    private OrganizationDepartmentDataRepository departmentRepository;
    private OrganizationDepartmentService departmentService;

    @BeforeEach
    void setUp() {
        departmentRepository = mock(OrganizationDepartmentDataRepository.class);
        departmentService = new OrganizationDepartmentService(departmentRepository);
    }

    @Test
    @DisplayName("创建部门时规范化编码和名称")
    void createDepartmentShouldNormalizeFields() {
        when(departmentRepository.findByDepartmentCode("DA")).thenReturn(null);
        when(departmentRepository.insert(any(OrganizationDepartment.class)))
                .thenAnswer(
                        invocation -> {
                            OrganizationDepartment department = invocation.getArgument(0);
                            department.setId(1L);
                            department.setCreatedAt(LocalDateTime.now());
                            department.setUpdatedAt(LocalDateTime.now());
                            return department;
                        });

        var response =
                departmentService.createDepartment(
                        new CreateOrganizationDepartmentRequest(" DA ", " 档案部 ", null, true, 10));

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.departmentCode()).isEqualTo("DA");
        assertThat(response.departmentName()).isEqualTo("档案部");
        assertThat(response.enabled()).isTrue();
        assertThat(response.sortOrder()).isEqualTo(10);
    }

    @Test
    @DisplayName("创建部门拒绝重复编码")
    void createDepartmentShouldRejectDuplicateCode() {
        when(departmentRepository.findByDepartmentCode("DA"))
                .thenReturn(department(1L, "DA", null));

        assertThatThrownBy(
                        () ->
                                departmentService.createDepartment(
                                        new CreateOrganizationDepartmentRequest(
                                                "DA", "档案部", null, true, 0)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("部门编码已存在");
    }

    @Test
    @DisplayName("更新父级拒绝选择自身")
    void updateDepartmentShouldRejectSelfParent() {
        when(departmentRepository.findById(1L)).thenReturn(Optional.of(department(1L, "DA", null)));

        assertThatThrownBy(
                        () ->
                                departmentService.updateDepartment(
                                        1L,
                                        new UpdateOrganizationDepartmentRequest(
                                                null, null, 1L, null, null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("父部门不能是当前部门");
    }

    @Test
    @DisplayName("更新父级拒绝选择后代")
    void updateDepartmentShouldRejectDescendantParent() {
        OrganizationDepartment root = department(1L, "ROOT", null);
        OrganizationDepartment child = department(2L, "CHILD", 1L);
        when(departmentRepository.findById(1L)).thenReturn(Optional.of(root));
        when(departmentRepository.findById(2L)).thenReturn(Optional.of(child));
        when(departmentRepository.list()).thenReturn(List.of(root, child));

        assertThatThrownBy(
                        () ->
                                departmentService.updateDepartment(
                                        1L,
                                        new UpdateOrganizationDepartmentRequest(
                                                null, null, 2L, null, null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("父部门不能是当前部门的下级部门");
    }

    @Test
    @DisplayName("更新父级为空时将子部门移动为根部门")
    void updateDepartmentShouldMoveChildToRoot() {
        OrganizationDepartment child = department(2L, "CHILD", 1L);
        when(departmentRepository.findById(2L)).thenReturn(Optional.of(child));
        when(departmentRepository.update(any(OrganizationDepartment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response =
                departmentService.updateDepartment(
                        2L, new UpdateOrganizationDepartmentRequest(null, null, null, null, null));

        assertThat(response.parentId()).isNull();
    }

    @Test
    @DisplayName("只更新启用状态时保留原父级")
    void updateDepartmentShouldPreserveParentWhenParentNotSpecified() {
        OrganizationDepartment child = department(2L, "CHILD", 1L);
        when(departmentRepository.findById(2L)).thenReturn(Optional.of(child));
        when(departmentRepository.update(any(OrganizationDepartment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response =
                departmentService.updateDepartment(
                        2L,
                        UpdateOrganizationDepartmentRequest.withoutParentChange(
                                null, null, false, null));

        assertThat(response.parentId()).isEqualTo(1L);
        assertThat(response.enabled()).isFalse();
    }

    @Test
    @DisplayName("校验启用部门拒绝停用部门")
    void requireEnabledDepartmentShouldRejectDisabledDepartment() {
        OrganizationDepartment department = department(1L, "DA", null);
        department.setEnabled(false);
        when(departmentRepository.findById(1L)).thenReturn(Optional.of(department));

        assertThatThrownBy(() -> departmentService.requireEnabledDepartment(1L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("部门已停用");
    }

    private static OrganizationDepartment department(Long id, String code, Long parentId) {
        OrganizationDepartment department = new OrganizationDepartment();
        department.setId(id);
        department.setDepartmentCode(code);
        department.setDepartmentName(code + "名称");
        department.setParentId(parentId);
        department.setEnabled(true);
        department.setSortOrder(0);
        department.setCreatedAt(LocalDateTime.now());
        department.setUpdatedAt(LocalDateTime.now());
        return department;
    }
}
