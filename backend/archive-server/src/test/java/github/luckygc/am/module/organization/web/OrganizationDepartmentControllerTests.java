package github.luckygc.am.module.organization.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

import github.luckygc.am.common.api.CollectionResponse;
import github.luckygc.am.common.security.AuthenticatedUser;
import github.luckygc.am.common.security.UnauthenticatedException;
import github.luckygc.am.module.authorization.service.AuthorizationPermissionCode;
import github.luckygc.am.module.authorization.service.AuthorizationPermissionService;
import github.luckygc.am.module.organization.service.OrganizationDepartmentService;
import github.luckygc.am.module.organization.service.OrganizationDepartmentService.CreateOrganizationDepartmentRequest;
import github.luckygc.am.module.organization.service.OrganizationDepartmentService.OrganizationDepartmentResponse;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@DisplayName("组织架构部门 HTTP 入口")
class OrganizationDepartmentControllerTests {

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    private final OrganizationDepartmentService departmentService =
            mock(OrganizationDepartmentService.class);
    private final AuthorizationPermissionService permissionService =
            mock(AuthorizationPermissionService.class);
    private final OrganizationDepartmentController controller =
            new OrganizationDepartmentController(departmentService, permissionService);

    @Test
    @DisplayName("未认证用户不能查询部门")
    void listDepartmentsShouldRequireAuthentication() {
        assertThatThrownBy(() -> controller.listDepartments(null, null))
                .isInstanceOf(UnauthenticatedException.class);

        verifyNoInteractions(departmentService);
        verifyNoInteractions(permissionService);
    }

    @Test
    @DisplayName("缺少相关权限时拒绝查询部门")
    void listDepartmentsShouldRequireRelatedPermission() {
        assertThatThrownBy(() -> controller.listDepartments(true, auth(9L)))
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        exception ->
                                assertThat(exception.getStatusCode())
                                        .isEqualTo(HttpStatus.FORBIDDEN));

        verifyNoInteractions(departmentService);
    }

    @Test
    @DisplayName("查询部门列表返回集合响应")
    void listDepartmentsShouldReturnCollectionResponse() {
        when(permissionService.hasPermission(
                        9L, AuthorizationPermissionCode.ORGANIZATION_DEPARTMENT_MANAGE.code()))
                .thenReturn(true);
        when(departmentService.listDepartments(true)).thenReturn(List.of(department(1L)));

        CollectionResponse<OrganizationDepartmentResponse> response =
                controller.listDepartments(true, auth(9L));

        assertThat(response.items()).containsExactly(department(1L));
    }

    @Test
    @DisplayName("用户管理权限可以查询部门选项")
    void listDepartmentsShouldAllowAuthenticationUserManagePermission() {
        when(permissionService.hasPermission(
                        9L, AuthorizationPermissionCode.AUTHENTICATION_USER_MANAGE.code()))
                .thenReturn(true);
        when(departmentService.listDepartments(true)).thenReturn(List.of(department(1L)));

        CollectionResponse<OrganizationDepartmentResponse> response =
                controller.listDepartments(true, auth(9L));

        assertThat(response.items()).containsExactly(department(1L));
    }

    @Test
    @DisplayName("创建部门要求组织架构管理权限")
    void createDepartmentShouldRequireOrganizationPermission() {
        CreateOrganizationDepartmentRequest request =
                new CreateOrganizationDepartmentRequest("D001", "综合部", null, true, 0);
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "权限不足"))
                .when(permissionService)
                .requirePermission(9L, AuthorizationPermissionCode.ORGANIZATION_DEPARTMENT_MANAGE);

        assertThatThrownBy(() -> controller.createDepartment(request, auth(9L)))
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        exception ->
                                assertThat(exception.getStatusCode())
                                        .isEqualTo(HttpStatus.FORBIDDEN));

        verifyNoInteractions(departmentService);
    }

    @Test
    @DisplayName("创建部门透传请求到服务")
    void createDepartmentShouldDelegateToService() {
        CreateOrganizationDepartmentRequest request =
                new CreateOrganizationDepartmentRequest("D001", "综合部", null, true, 0);
        OrganizationDepartmentResponse created = department(1L);
        when(departmentService.createDepartment(request)).thenReturn(created);

        OrganizationDepartmentResponse response = controller.createDepartment(request, auth(9L));

        assertThat(response).isEqualTo(created);
        verify(departmentService).createDepartment(request);
    }

    @Test
    @DisplayName("更新部门缺少 parentId 时不修改父级")
    void updateDepartmentShouldKeepParentWhenParentIdMissing() throws Exception {
        JsonNode request =
                JSON_MAPPER.readTree(
                        """
                        {"departmentName":"档案部","enabled":true}
                        """);
        when(departmentService.updateDepartment(
                        org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.any()))
                .thenReturn(department(1L));

        controller.updateDepartment(1L, request, auth(9L));

        ArgumentCaptor<OrganizationDepartmentService.UpdateOrganizationDepartmentRequest> captor =
                ArgumentCaptor.forClass(
                        OrganizationDepartmentService.UpdateOrganizationDepartmentRequest.class);
        verify(departmentService)
                .updateDepartment(org.mockito.ArgumentMatchers.eq(1L), captor.capture());
        assertThat(captor.getValue().departmentName()).isEqualTo("档案部");
        assertThat(captor.getValue().parentUpdate().changing()).isFalse();
    }

    @Test
    @DisplayName("更新部门显式 parentId 为 null 时清空父级")
    void updateDepartmentShouldClearParentWhenParentIdIsNull() throws Exception {
        JsonNode request =
                JSON_MAPPER.readTree(
                        """
                        {"parentId":null}
                        """);
        when(departmentService.updateDepartment(
                        org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.any()))
                .thenReturn(department(1L));

        controller.updateDepartment(1L, request, auth(9L));

        ArgumentCaptor<OrganizationDepartmentService.UpdateOrganizationDepartmentRequest> captor =
                ArgumentCaptor.forClass(
                        OrganizationDepartmentService.UpdateOrganizationDepartmentRequest.class);
        verify(departmentService)
                .updateDepartment(org.mockito.ArgumentMatchers.eq(1L), captor.capture());
        assertThat(captor.getValue().parentUpdate().changing()).isTrue();
        assertThat(captor.getValue().parentUpdate().parentId()).isNull();
    }

    @Test
    @DisplayName("PATCH 请求体使用 Jackson 3 JsonNode")
    void updateDepartmentShouldUseJackson3JsonNode() {
        Method method =
                List.of(OrganizationDepartmentController.class.getDeclaredMethods()).stream()
                        .filter(candidate -> candidate.getName().equals("updateDepartment"))
                        .findFirst()
                        .orElseThrow();

        assertThat(method.getParameterTypes()[1]).isEqualTo(tools.jackson.databind.JsonNode.class);
    }

    private TestingAuthenticationToken auth(Long userId) {
        return new TestingAuthenticationToken(
                new AuthenticatedUser() {
                    @Override
                    public Long id() {
                        return userId;
                    }

                    @Override
                    public String displayName() {
                        return "管理员";
                    }
                },
                null);
    }

    private OrganizationDepartmentResponse department(Long id) {
        LocalDateTime now = LocalDateTime.of(2026, 7, 3, 10, 0);
        return new OrganizationDepartmentResponse(id, "D001", "综合部", null, true, 0, now, now);
    }
}
