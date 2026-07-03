# Organization Departments Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将现有 `organization_unit` 半成品替换为部门口径的组织架构闭环，支持组织架构维护、用户所属部门、部门数据范围和前端页面。

**Architecture:** 新增 `module/organization` 作为组织架构业务模块，部门固定表使用 Jakarta Data Repository。`authentication`、`archive.authorization`、`archive.item` 只通过 `OrganizationDepartmentService` 依赖部门能力，旧 `OrganizationUnit`、`ORG_UNIT` 和 `/api/v1/organization-units` 不保留兼容分支。

**Tech Stack:** Spring Boot、Jakarta Persistence、Jakarta Data、MyBatis、Spring Security、OpenSpec、React、Ant Design、TanStack Query、Vite+。

---

## File Structure

- Create `openspec/changes/add-organization-departments/.openspec.yaml`: OpenSpec change metadata.
- Create `openspec/changes/add-organization-departments/proposal.md`: Scope and motivation.
- Create `openspec/changes/add-organization-departments/design.md`: Backend, data, API, UI decisions.
- Create `openspec/changes/add-organization-departments/tasks.md`: Track implementation tasks.
- Create `openspec/changes/add-organization-departments/specs/organization-departments/spec.md`: New organization department contract.
- Create `openspec/changes/add-organization-departments/specs/login-authentication/spec.md`: User department contract delta.
- Create `openspec/changes/add-organization-departments/specs/archive-data-scope/spec.md`: Department dimension and department subject scope delta.
- Modify `server/src/main/resources/db/migration/V20260703_0100__create_organization_unit.sql`: Replace old organization unit DDL with department target structure.
- Modify `server/src/main/resources/db/migration/V20260630_0100__create_authorization_permissions_and_archive_data_scope.sql`: Rename `ORG_UNIT` comments/seed expectations to `DEPARTMENT` and add organization permission seed.
- Create `server/src/main/java/github/luckygc/am/module/organization/**`: Entity, repository, service, web controller, package info.
- Delete `server/src/main/java/github/luckygc/am/module/archive/metadata/OrganizationUnit.java`.
- Delete `server/src/main/java/github/luckygc/am/module/archive/metadata/repository/OrganizationUnitDataRepository.java`.
- Modify `server/src/main/java/github/luckygc/am/module/archive/metadata/service/ArchiveMetadataService.java`: Remove organization unit DTO and query methods.
- Modify `server/src/main/java/github/luckygc/am/module/archive/authorization/**`: Rename `ORG_UNIT` dimension/subject to `DEPARTMENT`, validate via organization service, include user department subject scopes.
- Modify `server/src/main/java/github/luckygc/am/module/archive/item/**` and `server/src/main/resources/mapper/archive/ArchiveMapper.xml`: Rename `orgUnitId` / `org_unit_id` to `departmentId` / `department_id`.
- Modify `server/src/main/java/github/luckygc/am/module/authentication/**`: Add user `departmentId` and response display fields.
- Modify `server/src/main/java/github/luckygc/am/module/authorization/service/AuthorizationPermissionCode.java` and `AuthorizationPermissionService.java`: Add `organization:department:manage`.
- Create backend tests under `server/src/test/java/github/luckygc/am/module/organization/service/`.
- Modify archive authorization and mapper tests under `server/src/test/java/github/luckygc/am/module/archive/**`.
- Modify `web/src/shared/types/archive.ts` and `web/src/shared/api/archive.ts`: Department types and API functions.
- Create `web/src/pages/organization-departments/OrganizationDepartmentsPage.tsx` and test.
- Modify `web/src/app/routes.tsx` and `web/src/layout/AppShell.tsx`: Add system menu entry.
- Modify `web/src/pages/authentication-users/AuthenticationUsersPage.tsx` and test: Department column and selector.
- Modify `web/src/pages/archive-data-scopes/ArchiveDataScopesPage.tsx`: Department dimension.
- Modify archive item management components if `orgUnitId` appears in request mapping.

---

### Task 1: OpenSpec Contract

**Files:**
- Create: `openspec/changes/add-organization-departments/.openspec.yaml`
- Create: `openspec/changes/add-organization-departments/proposal.md`
- Create: `openspec/changes/add-organization-departments/design.md`
- Create: `openspec/changes/add-organization-departments/tasks.md`
- Create: `openspec/changes/add-organization-departments/specs/organization-departments/spec.md`
- Create: `openspec/changes/add-organization-departments/specs/login-authentication/spec.md`
- Create: `openspec/changes/add-organization-departments/specs/archive-data-scope/spec.md`

- [ ] **Step 1: Run validation before files exist**

Run:

```bash
openspec validate add-organization-departments --strict
```

Expected: FAIL because `openspec/changes/add-organization-departments` does not exist.

- [ ] **Step 2: Create change metadata and proposal**

Create `openspec/changes/add-organization-departments/.openspec.yaml`:

```yaml
id: add-organization-departments
status: in-progress
```

Create `openspec/changes/add-organization-departments/proposal.md`:

```markdown
# Add Organization Departments

## Why

The current organization model is named `organization_unit`, which is too abstract for system administration and leaks into API, frontend types, and archive data scope contracts. The system also lacks organization management UI and user department ownership, so department-scoped archive data permissions cannot be calculated from a user's department.

## What Changes

- Replace `OrganizationUnit` with `OrganizationDepartment`.
- Replace `am_organization_unit` with `am_organization_department`.
- Replace `ORG_UNIT` data scope dimension and subject with `DEPARTMENT`.
- Add organization department CRUD APIs and system menu page.
- Add `departmentId` to authentication users.
- Include the user's enabled department data scopes in runtime archive data scope resolution.

## Impact

- Backend modules: `organization`, `authentication`, `archive.authorization`, `archive.item`, `authorization`.
- Frontend pages: organization departments, authentication users, archive data scopes, archive item management.
- Database migrations are edited directly because the project is not released and old `organization_unit` compatibility is explicitly out of scope.
```

- [ ] **Step 3: Create design and task artifacts**

Create `openspec/changes/add-organization-departments/design.md`:

```markdown
# Organization Departments Design

## Decisions

### Department is the organization node

The organization hierarchy uses department terminology across storage, Java, API, and frontend contracts. `unit` is not retained as a compatibility alias.

### Organization module owns departments

Department entity, repository, service, and controller live under `github.luckygc.am.module.organization`. Other modules call `OrganizationDepartmentService` for validation and display data.

### Direct department scope only

Runtime archive data scopes include scopes directly bound to the user's enabled department. Parent department bindings do not include child department users in this change.

### Department references are historical-safe

Disabled departments can remain referenced by existing users, records, and data scope rows. Disabled departments cannot be selected for new user ownership, archive writes, or data scope conditions.
```

Create `openspec/changes/add-organization-departments/tasks.md`:

```markdown
## 1. Contract

- [ ] 1.1 Add OpenSpec department, authentication user, and archive data scope deltas.
- [ ] 1.2 Validate OpenSpec with strict mode.

## 2. Backend

- [ ] 2.1 Replace organization unit migration and model with organization department.
- [ ] 2.2 Add department service, API, permission, and tests.
- [ ] 2.3 Add user department ownership.
- [ ] 2.4 Replace archive data scope `ORG_UNIT` with `DEPARTMENT`.
- [ ] 2.5 Rename archive item and volume department fields.

## 3. Frontend

- [ ] 3.1 Add department API and types.
- [ ] 3.2 Add organization departments page and menu.
- [ ] 3.3 Update user management department display and edit flow.
- [ ] 3.4 Update data scope and archive item department fields.

## 4. Verification

- [ ] 4.1 Run OpenSpec strict validation.
- [ ] 4.2 Run backend compile and focused tests.
- [ ] 4.3 Run frontend check and focused tests.
- [ ] 4.4 Search and remove old organization unit symbols.
```

- [ ] **Step 4: Create OpenSpec requirement deltas**

Create `openspec/changes/add-organization-departments/specs/organization-departments/spec.md`:

```markdown
## ADDED Requirements

### Requirement: 组织架构部门管理

系统 SHALL 使用部门作为组织架构节点，并提供部门创建、查询、更新、启停和排序能力。

#### Scenario: 创建部门

- **WHEN** 管理员提交部门编码、部门名称、父部门和排序
- **THEN** 系统 SHALL 创建 `OrganizationDepartment`
- **AND** 部门编码 SHALL 唯一
- **AND** 父部门为空时 SHALL 创建根部门
- **AND** 父部门不为空时 SHALL 校验父部门存在

#### Scenario: 更新部门父级

- **WHEN** 管理员更新部门父级
- **THEN** 系统 SHALL 拒绝将父级设置为自己
- **AND** 系统 SHALL 拒绝将父级设置为自己的后代

#### Scenario: 停用部门

- **WHEN** 管理员停用部门
- **THEN** 系统 SHALL 保留历史引用
- **AND** 系统 SHALL NOT 允许该部门被新用户归属、新档案记录或新数据范围条件选择

#### Scenario: 查询启用部门

- **WHEN** 客户端请求启用部门列表
- **THEN** 系统 SHALL 只返回 `enabled=true` 的部门
- **AND** 响应 SHALL 使用 `CollectionResponse`

### Requirement: 组织架构权限

系统 SHALL 使用独立功能权限控制组织架构管理。

#### Scenario: 管理组织架构

- **WHEN** 用户调用组织架构管理 API
- **THEN** 系统 SHALL 要求 `organization:department:manage`
- **AND** 超级管理员 SHALL 默认拥有该权限
```

Create `openspec/changes/add-organization-departments/specs/login-authentication/spec.md`:

```markdown
## MODIFIED Requirements

### Requirement: 用户管理

认证用户管理 SHALL 支持用户所属部门。

#### Scenario: 创建或更新用户所属部门

- **WHEN** 管理员为用户设置所属部门
- **THEN** 系统 SHALL 校验部门存在且启用
- **AND** 用户可以没有所属部门

#### Scenario: 返回用户所属部门

- **WHEN** 客户端查询用户列表或详情
- **THEN** 响应 SHALL 包含 `departmentId`
- **AND** 响应 SHOULD 包含 `departmentCode` 和 `departmentName` 供界面展示
```

Create `openspec/changes/add-organization-departments/specs/archive-data-scope/spec.md`:

```markdown
## MODIFIED Requirements

### Requirement: 固定维度范围条件

系统 SHALL 使用部门作为档案所属组织维度。

#### Scenario: 保存档案所属部门范围

- **WHEN** 管理员选择档案所属部门范围
- **THEN** 系统 SHALL 保存维度类型 `DEPARTMENT`
- **AND** 系统 SHALL 校验部门存在且启用
- **AND** 查询编译时 SHALL 将部门 ID 条件应用到档案主表 `department_id`

### Requirement: 授权主体绑定档案数据范围

系统 SHALL 支持用户所属部门绑定的数据范围参与用户有效数据范围计算。

#### Scenario: 用户所属部门范围参与计算

- **WHEN** 用户所属部门启用且部门绑定了启用数据范围
- **THEN** 系统 SHALL 将该部门范围与用户直接范围、角色范围按 OR 语义合并

#### Scenario: 停用所属部门范围不参与计算

- **WHEN** 用户所属部门已停用
- **THEN** 系统 SHALL NOT 将该部门主体绑定的数据范围计入用户有效数据范围
```

- [ ] **Step 5: Validate OpenSpec**

Run:

```bash
openspec validate add-organization-departments --strict
```

Expected: PASS.

- [ ] **Step 6: Commit contract**

```bash
git add openspec/changes/add-organization-departments
git commit -m "spec: add organization department contract"
```

---

### Task 2: Department Persistence and Service

**Files:**
- Create: `server/src/main/java/github/luckygc/am/module/organization/OrganizationDepartment.java`
- Create: `server/src/main/java/github/luckygc/am/module/organization/repository/OrganizationDepartmentDataRepository.java`
- Create: `server/src/main/java/github/luckygc/am/module/organization/service/OrganizationDepartmentService.java`
- Create: `server/src/main/java/github/luckygc/am/module/organization/package-info.java`
- Create: `server/src/main/java/github/luckygc/am/module/organization/repository/package-info.java`
- Create: `server/src/main/java/github/luckygc/am/module/organization/service/package-info.java`
- Test: `server/src/test/java/github/luckygc/am/module/organization/service/OrganizationDepartmentServiceTests.java`
- Modify: `server/src/main/resources/db/migration/V20260703_0100__create_organization_unit.sql`
- Delete: `server/src/main/java/github/luckygc/am/module/archive/metadata/OrganizationUnit.java`
- Delete: `server/src/main/java/github/luckygc/am/module/archive/metadata/repository/OrganizationUnitDataRepository.java`

- [ ] **Step 1: Write failing service tests**

Create `server/src/test/java/github/luckygc/am/module/organization/service/OrganizationDepartmentServiceTests.java`:

```java
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
                .thenAnswer(invocation -> {
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
        when(departmentRepository.findByDepartmentCode("DA")).thenReturn(department(1L, "DA", null));

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
        when(departmentRepository.findById(1L))
                .thenReturn(Optional.of(department(1L, "DA", null)));

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
        when(departmentRepository.findById(1L))
                .thenReturn(Optional.of(department(1L, "ROOT", null)));
        when(departmentRepository.list()).thenReturn(List.of(department(1L, "ROOT", null), department(2L, "CHILD", 1L)));

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
```

- [ ] **Step 2: Run tests to verify failure**

Run:

```bash
cd server && mvn -q -Dtest=OrganizationDepartmentServiceTests test
```

Expected: FAIL because `OrganizationDepartmentService` and related classes do not exist.

- [ ] **Step 3: Replace migration target structure**

Edit `server/src/main/resources/db/migration/V20260703_0100__create_organization_unit.sql` to this content:

```sql
create table am_organization_department
(
    id              bigserial primary key,
    department_code varchar(100) not null,
    department_name varchar(255) not null,
    parent_id       bigint references am_organization_department (id),
    enabled         boolean      not null default true,
    sort_order      integer      not null default 0,
    version         integer      not null default 0,
    created_at      timestamp    not null default localtimestamp,
    updated_at      timestamp    not null default localtimestamp
);

create unique index uk_am_organization_department_code on am_organization_department (department_code);
create index idx_am_organization_department_parent
    on am_organization_department (parent_id, sort_order, id);

comment on table am_organization_department is '组织架构部门表';
comment on column am_organization_department.department_code is '部门编码';
comment on column am_organization_department.department_name is '部门名称';
comment on column am_organization_department.parent_id is '父部门 ID';
comment on column am_organization_department.enabled is '是否启用';

alter table am_archive_volume
    rename column org_unit_id to department_id;

alter table am_archive_item
    rename column org_unit_id to department_id;

comment on column am_archive_volume.department_id is '所属部门 ID';
comment on column am_archive_item.department_id is '所属部门 ID';

alter table am_archive_volume
    add constraint fk_am_archive_volume_department
        foreign key (department_id) references am_organization_department (id);

alter table am_archive_item
    add constraint fk_am_archive_item_department
        foreign key (department_id) references am_organization_department (id);
```

- [ ] **Step 4: Create department entity and repository**

Create `server/src/main/java/github/luckygc/am/module/organization/OrganizationDepartment.java`:

```java
package github.luckygc.am.module.organization;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.jspecify.annotations.Nullable;

import lombok.Data;

@Data
@Entity
@Table(name = "am_organization_department")
public class OrganizationDepartment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "department_code", nullable = false, length = 100)
    private String departmentCode;

    @Column(name = "department_name", nullable = false)
    private String departmentName;

    @Column(name = "parent_id")
    private @Nullable Long parentId;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Version
    @Column(nullable = false)
    private int version;

    @Column(name = "created_at", nullable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
```

Create `server/src/main/java/github/luckygc/am/module/organization/repository/OrganizationDepartmentDataRepository.java`:

```java
package github.luckygc.am.module.organization.repository;

import java.util.List;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.data.repository.Find;
import jakarta.data.repository.OrderBy;
import jakarta.data.repository.Param;
import jakarta.data.repository.Repository;

import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import github.luckygc.am.common.repository.DataRepository;
import github.luckygc.am.module.organization.OrganizationDepartment;

@Transactional(rollbackFor = Throwable.class, isolation = Isolation.READ_COMMITTED)
@Repository
public interface OrganizationDepartmentDataRepository
        extends DataRepository<OrganizationDepartment, Long> {

    @Transactional(readOnly = true)
    @Find
    @OrderBy("sortOrder")
    @OrderBy("id")
    List<OrganizationDepartment> list();

    @Transactional(readOnly = true)
    @Find
    @OrderBy("sortOrder")
    @OrderBy("id")
    List<OrganizationDepartment> list(@Param("enabled") @Nullable Boolean enabled);

    @Transactional(readOnly = true)
    @Find
    @Nullable OrganizationDepartment findByDepartmentCode(@Nonnull String departmentCode);
}
```

- [ ] **Step 5: Create department service**

Create `server/src/main/java/github/luckygc/am/module/organization/service/OrganizationDepartmentService.java` with these records and methods:

```java
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

    public OrganizationDepartmentService(OrganizationDepartmentDataRepository departmentRepository) {
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
    public OrganizationDepartmentResponse createDepartment(CreateOrganizationDepartmentRequest request) {
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
            throw new BadRequestException(
                    "父部门不能是当前部门的下级部门", "parentId", "父部门不能是当前部门的下级部门");
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
```

- [ ] **Step 6: Add package annotations**

Create `package-info.java` files:

```java
/**
 * 组织架构模块，承载部门树和用户所属部门等业务语义。
 */
@NullMarked
package github.luckygc.am.module.organization;

import org.jspecify.annotations.NullMarked;
```

```java
/**
 * 组织架构固定表 Repository。
 */
@NullMarked
package github.luckygc.am.module.organization.repository;

import org.jspecify.annotations.NullMarked;
```

```java
/**
 * 组织架构服务，负责部门维护、部门校验和跨模块部门查询。
 */
@NullMarked
package github.luckygc.am.module.organization.service;

import org.jspecify.annotations.NullMarked;
```

- [ ] **Step 7: Delete old organization unit classes**

Delete:

```text
server/src/main/java/github/luckygc/am/module/archive/metadata/OrganizationUnit.java
server/src/main/java/github/luckygc/am/module/archive/metadata/repository/OrganizationUnitDataRepository.java
```

Do not update `ArchiveMetadataService` in this task beyond removing constructor parameters that reference the deleted repository if the compile failure blocks this task's test. Full metadata cleanup is Task 4.

- [ ] **Step 8: Run service tests**

Run:

```bash
cd server && mvn -q -Dtest=OrganizationDepartmentServiceTests test
```

Expected: PASS.

- [ ] **Step 9: Commit department persistence**

```bash
git add server/src/main/resources/db/migration/V20260703_0100__create_organization_unit.sql \
  server/src/main/java/github/luckygc/am/module/organization \
  server/src/main/java/github/luckygc/am/module/archive/metadata/OrganizationUnit.java \
  server/src/main/java/github/luckygc/am/module/archive/metadata/repository/OrganizationUnitDataRepository.java \
  server/src/test/java/github/luckygc/am/module/organization/service/OrganizationDepartmentServiceTests.java
git commit -m "feat: add organization department model"
```

---

### Task 3: Department API and Permission

**Files:**
- Create: `server/src/main/java/github/luckygc/am/module/organization/web/OrganizationDepartmentController.java`
- Create: `server/src/main/java/github/luckygc/am/module/organization/web/package-info.java`
- Modify: `server/src/main/java/github/luckygc/am/module/authorization/service/AuthorizationPermissionCode.java`
- Modify: `server/src/main/java/github/luckygc/am/module/authorization/service/AuthorizationPermissionService.java`
- Modify: `server/src/main/resources/db/migration/V20260630_0100__create_authorization_permissions_and_archive_data_scope.sql`
- Test: `server/src/test/java/github/luckygc/am/module/authorization/service/AuthorizationPermissionServiceTests.java`

- [ ] **Step 1: Write failing permission catalog test**

Create `server/src/test/java/github/luckygc/am/module/authorization/service/AuthorizationPermissionServiceTests.java`:

```java
package github.luckygc.am.module.authorization.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import github.luckygc.am.module.authorization.repository.AuthorizationPermissionDataRepository;
import github.luckygc.am.module.authorization.repository.AuthorizationRoleDataRepository;
import github.luckygc.am.module.authorization.repository.AuthorizationRolePermissionRelationDataRepository;
import github.luckygc.am.module.authorization.repository.AuthorizationUserRoleRelationDataRepository;

@DisplayName("功能权限服务")
class AuthorizationPermissionServiceTests {

    @Test
    @DisplayName("权限目录包含组织架构管理权限")
    void catalogShouldContainDepartmentManagePermission() {
        AuthorizationPermissionService service =
                new AuthorizationPermissionService(
                        mock(AuthorizationPermissionDataRepository.class),
                        mock(AuthorizationRoleDataRepository.class),
                        mock(AuthorizationRolePermissionRelationDataRepository.class),
                        mock(AuthorizationUserRoleRelationDataRepository.class));

        assertThat(service.listPermissionCatalog())
                .anySatisfy(
                        permission -> {
                            assertThat(permission.permissionCode())
                                    .isEqualTo("organization:department:manage");
                            assertThat(permission.permissionName()).isEqualTo("管理组织架构");
                            assertThat(permission.moduleCode()).isEqualTo("organization");
                        });
    }
}
```

- [ ] **Step 2: Run test to verify failure**

Run:

```bash
cd server && mvn -q -Dtest=AuthorizationPermissionServiceTests test
```

Expected: FAIL because the permission catalog does not include `organization:department:manage`.

- [ ] **Step 3: Add permission enum and catalog row**

In `AuthorizationPermissionCode.java`, add:

```java
ORGANIZATION_DEPARTMENT_MANAGE("organization:department:manage");
```

Place it after `AUTHORIZATION_ROLE_MANAGE` and add a comma to the previous enum constant.

In `AuthorizationPermissionService.PERMISSION_CATALOG`, add:

```java
new PermissionDefinition(
        "organization:department:manage", "管理组织架构", "organization", "维护组织架构部门树")
```

In `V20260630_0100__create_authorization_permissions_and_archive_data_scope.sql`, add the same row to `insert into am_authorization_permission`:

```sql
('organization:department:manage', '管理组织架构', 'organization', '维护组织架构部门树')
```

Ensure the previous row has a trailing comma and the new final row ends with semicolon.

- [ ] **Step 4: Create department controller**

Create `server/src/main/java/github/luckygc/am/module/organization/web/OrganizationDepartmentController.java`:

```java
package github.luckygc.am.module.organization.web;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import github.luckygc.am.common.api.CollectionResponse;
import github.luckygc.am.common.security.AuthenticatedUsers;
import github.luckygc.am.module.authorization.service.AuthorizationPermissionCode;
import github.luckygc.am.module.authorization.service.AuthorizationPermissionService;
import github.luckygc.am.module.organization.service.OrganizationDepartmentService;
import github.luckygc.am.module.organization.service.OrganizationDepartmentService.CreateOrganizationDepartmentRequest;
import github.luckygc.am.module.organization.service.OrganizationDepartmentService.OrganizationDepartmentResponse;
import github.luckygc.am.module.organization.service.OrganizationDepartmentService.UpdateOrganizationDepartmentRequest;

@RestController
public class OrganizationDepartmentController {

    private final OrganizationDepartmentService departmentService;
    private final AuthorizationPermissionService permissionService;

    public OrganizationDepartmentController(
            OrganizationDepartmentService departmentService,
            AuthorizationPermissionService permissionService) {
        this.departmentService = departmentService;
        this.permissionService = permissionService;
    }

    @GetMapping("/api/v1/organization-departments")
    public CollectionResponse<OrganizationDepartmentResponse> listDepartments(
            @RequestParam(required = false) @Nullable Boolean enabled,
            @Nullable Authentication authentication) {
        requireReadable(authentication);
        return CollectionResponse.of(departmentService.listDepartments(enabled));
    }

    @GetMapping("/api/v1/organization-departments/{department}")
    public OrganizationDepartmentResponse getDepartment(
            @PathVariable Long department, @Nullable Authentication authentication) {
        requireManage(authentication);
        return departmentService.getDepartment(department);
    }

    @PostMapping("/api/v1/organization-departments")
    @ResponseStatus(HttpStatus.CREATED)
    public OrganizationDepartmentResponse createDepartment(
            @RequestBody CreateOrganizationDepartmentRequest request,
            @Nullable Authentication authentication) {
        requireManage(authentication);
        return departmentService.createDepartment(request);
    }

    @PatchMapping("/api/v1/organization-departments/{department}")
    public OrganizationDepartmentResponse updateDepartment(
            @PathVariable Long department,
            @RequestBody UpdateOrganizationDepartmentRequest request,
            @Nullable Authentication authentication) {
        requireManage(authentication);
        return departmentService.updateDepartment(department, request);
    }

    private void requireReadable(@Nullable Authentication authentication) {
        Long userId =
                AuthenticatedUsers.requireUserId(
                        authentication == null ? null : authentication.getPrincipal());
        if (permissionService.hasPermission(
                        userId, AuthorizationPermissionCode.ORGANIZATION_DEPARTMENT_MANAGE.code())
                || permissionService.hasPermission(
                        userId, AuthorizationPermissionCode.ARCHIVE_DATA_SCOPE_MANAGE.code())
                || permissionService.hasPermission(
                        userId, AuthorizationPermissionCode.AUTHENTICATION_USER_MANAGE.code())) {
            return;
        }
        permissionService.requirePermission(
                userId, AuthorizationPermissionCode.ORGANIZATION_DEPARTMENT_MANAGE);
    }

    private void requireManage(@Nullable Authentication authentication) {
        permissionService.requirePermission(
                AuthenticatedUsers.requireUserId(
                        authentication == null ? null : authentication.getPrincipal()),
                AuthorizationPermissionCode.ORGANIZATION_DEPARTMENT_MANAGE);
    }
}
```

Create `server/src/main/java/github/luckygc/am/module/organization/web/package-info.java`:

```java
/**
 * 组织架构 HTTP 接口。
 */
@NullMarked
package github.luckygc.am.module.organization.web;

import org.jspecify.annotations.NullMarked;
```

- [ ] **Step 5: Run permission test and architecture test**

Run:

```bash
cd server && mvn -q -Dtest=AuthorizationPermissionServiceTests,ArchitectureRulesTest test
```

Expected: PASS.

- [ ] **Step 6: Commit department API**

```bash
git add server/src/main/java/github/luckygc/am/module/organization/web \
  server/src/main/java/github/luckygc/am/module/authorization/service/AuthorizationPermissionCode.java \
  server/src/main/java/github/luckygc/am/module/authorization/service/AuthorizationPermissionService.java \
  server/src/main/resources/db/migration/V20260630_0100__create_authorization_permissions_and_archive_data_scope.sql \
  server/src/test/java/github/luckygc/am/module/authorization/service/AuthorizationPermissionServiceTests.java
git commit -m "feat: add organization department api"
```

---

### Task 4: Archive Department Rename

**Files:**
- Modify: `server/src/main/java/github/luckygc/am/module/archive/authorization/ArchiveDataScopeDimensionType.java`
- Modify: `server/src/main/java/github/luckygc/am/module/archive/authorization/ArchiveDataScopeSubjectType.java`
- Modify: `server/src/main/java/github/luckygc/am/module/archive/authorization/service/ArchiveDataScopeService.java`
- Modify: `server/src/main/java/github/luckygc/am/module/archive/authorization/web/ArchiveDataScopeController.java`
- Modify: `server/src/main/java/github/luckygc/am/module/archive/mapper/ArchiveDataScopeSqlGroup.java`
- Modify: `server/src/main/java/github/luckygc/am/module/archive/mapper/ArchiveMapper.java`
- Modify: `server/src/main/resources/mapper/archive/ArchiveMapper.xml`
- Modify: `server/src/main/java/github/luckygc/am/module/archive/item/ArchiveItem.java`
- Modify: `server/src/main/java/github/luckygc/am/module/archive/item/ArchiveVolume.java`
- Modify: `server/src/main/java/github/luckygc/am/module/archive/item/service/ArchiveItemRoutingService.java`
- Modify: `server/src/main/java/github/luckygc/am/module/archive/item/service/ArchiveItemImportExportService.java`
- Modify: `server/src/main/java/github/luckygc/am/module/archive/metadata/service/ArchiveMetadataService.java`
- Test: `server/src/test/java/github/luckygc/am/module/archive/authorization/service/ArchiveDataScopeServiceTests.java`
- Test: `server/src/test/java/github/luckygc/am/module/archive/mapper/ArchiveMapperDataScopeSqlTests.java`

- [ ] **Step 1: Update failing archive data scope tests**

In `ArchiveDataScopeServiceTests`, replace organization unit test names and assertions with department terminology. Add this test:

```java
@Test
@DisplayName("部门范围拒绝停用部门")
void validateScopeShouldRejectDisabledDepartmentDimension() {
    ArchiveDataScope scope = scope(1L, ArchiveDataScopeType.CONDITIONAL, true);
    ArchiveDataScopeDimension dimension = new ArchiveDataScopeDimension();
    dimension.setDimensionType(ArchiveDataScopeDimensionType.DEPARTMENT);
    dimension.setTargetId(5L);
    when(departmentService.requireEnabledDepartment(5L))
            .thenThrow(new BadRequestException("部门已停用", "departmentId", "部门已停用"));

    assertThatThrownBy(() -> dataScopeService.validateScopeDefinition(scope, List.of(dimension)))
            .isInstanceOf(BadRequestException.class)
            .hasMessageContaining("部门已停用");
}
```

Update the test fixture constructor to pass a mocked `OrganizationDepartmentService departmentService` into `ArchiveDataScopeService`.

In `ArchiveMapperDataScopeSqlTests`, rename the display name and expected SQL:

```java
@DisplayName("部门范围生成主表 department_id 谓词")
void listDynamicItemsShouldApplyDepartmentDataScope() throws Exception {
    parameters.put(
            "dataScopeGroups",
            List.of(new ArchiveDataScopeSqlGroup(List.of(), List.of(), List.of(), List.of(5L), List.of())));

    String sql = statement.getBoundSql(parameters).getSql().replaceAll("\\s+", " ");

    assertThat(sql).contains("i.department_id in ( ? )");
}
```

- [ ] **Step 2: Run tests to verify failure**

Run:

```bash
cd server && mvn -q -Dtest=ArchiveDataScopeServiceTests,ArchiveMapperDataScopeSqlTests test
```

Expected: FAIL because `DEPARTMENT`, `departmentService`, and SQL rename are not implemented.

- [ ] **Step 3: Rename enums and service dependencies**

In `ArchiveDataScopeDimensionType.java`, replace `ORG_UNIT` with:

```java
DEPARTMENT
```

In `ArchiveDataScopeSubjectType.java`, replace `ORG_UNIT` with:

```java
DEPARTMENT
```

In `ArchiveDataScopeService`, replace `ArchiveMetadataService.OrganizationUnitDto` imports and validation with `OrganizationDepartmentService`. Constructor adds:

```java
private final OrganizationDepartmentService departmentService;
```

Use this validation branch:

```java
case DEPARTMENT -> {
    if (dimension.getTargetId() == null) {
        throw new BadRequestException("部门范围必须指定部门 ID", "dimensions", "部门范围必须指定部门 ID");
    }
    departmentService.requireEnabledDepartment(dimension.getTargetId());
}
```

In range compilation, rename local lists:

```java
List<Long> scopeDepartmentIds = new ArrayList<>();
```

Use branch:

```java
case DEPARTMENT -> {
    if (dimension.getTargetId() != null) {
        scopeDepartmentIds.add(dimension.getTargetId());
    }
}
```

- [ ] **Step 4: Rename mapper group and SQL**

In `ArchiveDataScopeSqlGroup.java`, rename record component from `orgUnitIds` to:

```java
List<Long> departmentIds
```

In `ArchiveMapper.xml`, replace:

```xml
group.orgUnitIds
dataScopeOrgUnitId
i.org_unit_id
```

with:

```xml
group.departmentIds
dataScopeDepartmentId
i.department_id
```

In `ArchiveMapper.java`, replace method parameters named `orgUnitId` with `departmentId`.

- [ ] **Step 5: Rename archive item and volume fixed fields**

In `ArchiveItem.java` and `ArchiveVolume.java`, rename:

```java
@Column(name = "org_unit_id")
private Long orgUnitId;
```

to:

```java
@Column(name = "department_id")
private Long departmentId;
```

In request/response records under `ArchiveItemRoutingService`, rename `orgUnitId` to `departmentId`. Replace validation method with:

```java
private @Nullable Long validateDepartmentForWrite(@Nullable Long departmentId) {
    if (departmentId == null) {
        return null;
    }
    if (departmentId <= 0) {
        throw badRequest("部门不合法", "departmentId", "部门不合法");
    }
    return departmentService.requireEnabledDepartment(departmentId).id();
}
```

Add `OrganizationDepartmentService departmentService` as a constructor dependency where `ArchiveItemRoutingService` currently uses `ArchiveMetadataService` for organization unit validation.

- [ ] **Step 6: Remove organization unit API from data scope controller**

Delete this method from `ArchiveDataScopeController`:

```java
@GetMapping("/api/v1/organization-units")
public CollectionResponse<ArchiveMetadataService.OrganizationUnitDto> listOrganizationUnits(
        @RequestParam(defaultValue = "true") boolean enabled,
        @Nullable Authentication authentication) {
    requirePermission(authentication);
    return CollectionResponse.of(archiveMetadataService.listOrganizationUnits(enabled));
}
```

Remove `OrganizationUnitDto`, `listOrganizationUnits`, and repository constructor wiring from `ArchiveMetadataService`.

- [ ] **Step 7: Run archive tests**

Run:

```bash
cd server && mvn -q -Dtest=ArchiveDataScopeServiceTests,ArchiveMapperDataScopeSqlTests test
```

Expected: PASS.

- [ ] **Step 8: Search old backend names**

Run:

```bash
rg -n "OrganizationUnit|organization-units|ORG_UNIT|orgUnit|org_unit|unitCode|unitName" server/src/main/java server/src/test/java server/src/main/resources
```

Expected: no matches in project-owned source, except historical text inside the design document is outside this search path.

- [ ] **Step 9: Commit archive rename**

```bash
git add server/src/main/java/github/luckygc/am/module/archive \
  server/src/main/resources/mapper/archive/ArchiveMapper.xml \
  server/src/test/java/github/luckygc/am/module/archive
git commit -m "refactor: rename archive organization scope to department"
```

---

### Task 5: Authentication User Department

**Files:**
- Modify: `server/src/main/java/github/luckygc/am/module/authentication/AuthenticationUser.java`
- Modify: `server/src/main/java/github/luckygc/am/module/authentication/service/AuthenticationUserManagementService.java`
- Modify: `server/src/main/java/github/luckygc/am/module/authentication/web/AuthenticationUserManagementController.java`
- Modify: database migration that creates `am_authentication_user`
- Test: `server/src/test/java/github/luckygc/am/module/authentication/AuthenticationUserManagementServiceTests.java`

- [ ] **Step 1: Write failing user department service tests**

Create `AuthenticationUserManagementServiceTests` with a focused create/update test:

```java
package github.luckygc.am.module.authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import github.luckygc.am.module.authentication.repository.AuthenticationUserDataRepository;
import github.luckygc.am.module.authentication.service.AuthenticationUserManagementService;
import github.luckygc.am.module.authentication.service.AuthenticationUserManagementService.CreateAuthenticationUserRequest;
import github.luckygc.am.module.authorization.repository.AuthorizationRoleDataRepository;
import github.luckygc.am.module.authorization.repository.AuthorizationUserRoleRelationDataRepository;
import github.luckygc.am.module.authorization.service.AuthorizationPermissionService;
import github.luckygc.am.module.organization.service.OrganizationDepartmentService;
import github.luckygc.am.module.organization.service.OrganizationDepartmentService.OrganizationDepartmentResponse;

@DisplayName("认证用户管理服务")
class AuthenticationUserManagementServiceTests {

    @Test
    @DisplayName("创建用户保存所属部门")
    void createUserShouldSaveDepartment() {
        AuthenticationUserDataRepository userRepository = mock(AuthenticationUserDataRepository.class);
        OrganizationDepartmentService departmentService = mock(OrganizationDepartmentService.class);
        when(departmentService.requireEnabledDepartment(3L))
                .thenReturn(new OrganizationDepartmentResponse(3L, "DA", "档案部", null, true, 0, LocalDateTime.now(), LocalDateTime.now()));
        when(userRepository.findOptionalByUsername("zhangsan")).thenReturn(null);
        when(userRepository.insert(any(AuthenticationUser.class)))
                .thenAnswer(invocation -> {
                    AuthenticationUser user = invocation.getArgument(0);
                    user.setId(7L);
                    user.setCreatedAt(LocalDateTime.now());
                    return user;
                });

        AuthenticationUserManagementService service =
                createService(userRepository, departmentService);

        var response =
                service.createUser(
                        new CreateAuthenticationUserRequest(
                                "zhangsan", "Secret123", "张三", null, null, 3L),
                        1L);

        assertThat(response.departmentId()).isEqualTo(3L);
        assertThat(response.departmentName()).isEqualTo("档案部");
    }

    private static AuthenticationUserManagementService createService(
            AuthenticationUserDataRepository userRepository,
            OrganizationDepartmentService departmentService) {
        AuthorizationPermissionService permissionService = mock(AuthorizationPermissionService.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        when(passwordEncoder.encode("Secret123")).thenReturn("encoded-password");
        return new AuthenticationUserManagementService(
                userRepository,
                mock(AuthorizationRoleDataRepository.class),
                mock(AuthorizationUserRoleRelationDataRepository.class),
                permissionService,
                passwordEncoder,
                departmentService);
    }
}
```

- [ ] **Step 2: Run test to verify failure**

Run:

```bash
cd server && mvn -q -Dtest=AuthenticationUserManagementServiceTests test
```

Expected: FAIL because user request and response records do not include department fields.

- [ ] **Step 3: Add database column and entity field**

In the migration that creates `am_authentication_user`, add:

```sql
department_id bigint references am_organization_department (id),
```

Add comment:

```sql
comment on column am_authentication_user.department_id is '所属部门 ID';
```

In `AuthenticationUser.java`, add:

```java
@Column(name = "department_id")
private Long departmentId;
```

- [ ] **Step 4: Add service dependency and DTO fields**

In `AuthenticationUserManagementService`, add constructor dependency:

```java
private final OrganizationDepartmentService departmentService;
```

Extend request records:

```java
public record CreateAuthenticationUserRequest(
        String username,
        String password,
        @Nullable String displayName,
        @Nullable String email,
        @Nullable String mobilePhone,
        @Nullable Long departmentId) {}

public record UpdateAuthenticationUserRequest(
        @Nullable String displayName,
        @Nullable String email,
        @Nullable String mobilePhone,
        @Nullable Boolean enabled,
        @Nullable Long departmentId) {}
```

Use this helper:

```java
private @Nullable OrganizationDepartmentResponse validateDepartment(@Nullable Long departmentId) {
    return departmentId == null ? null : departmentService.requireEnabledDepartment(departmentId);
}
```

In create/update, set:

```java
OrganizationDepartmentResponse department = validateDepartment(request.departmentId());
user.setDepartmentId(department == null ? null : department.id());
```

Extend user DTOs with:

```java
@Nullable Long departmentId,
@Nullable String departmentCode,
@Nullable String departmentName
```

Map display fields by calling `departmentService.getDepartment(user.getDepartmentId())` when `departmentId` is not null. If the department no longer exists, return null display fields and keep `departmentId`.

- [ ] **Step 5: Run user tests**

Run:

```bash
cd server && mvn -q -Dtest=AuthenticationUserManagementServiceTests test
```

Expected: PASS.

- [ ] **Step 6: Commit user department support**

```bash
git add server/src/main/java/github/luckygc/am/module/authentication \
  server/src/main/resources/db/migration \
  server/src/test/java/github/luckygc/am/module/authentication/AuthenticationUserManagementServiceTests.java
git commit -m "feat: add user department ownership"
```

---

### Task 6: Department Runtime Data Scopes

**Files:**
- Modify: `server/src/main/java/github/luckygc/am/module/archive/authorization/service/ArchiveDataScopeService.java`
- Modify: `server/src/test/java/github/luckygc/am/module/archive/authorization/service/ArchiveDataScopeServiceTests.java`

- [ ] **Step 1: Write failing runtime scope tests**

Add these tests to `ArchiveDataScopeServiceTests`:

```java
@Test
@DisplayName("用户所属部门绑定的数据范围参与范围计算")
void resolveUserScopeShouldIncludeDepartmentScopes() {
    AuthenticationUser user = new AuthenticationUser();
    user.setId(7L);
    user.setDepartmentId(3L);
    user.setEnabled(true);
    when(authenticationUserRepository.findById(7L)).thenReturn(Optional.of(user));
    when(departmentService.getDepartment(3L))
            .thenReturn(department(3L, true));
    when(subjectRelationRepository.findBySubjectTypeAndSubjectId(
                    ArchiveDataScopeSubjectType.USER, 7L))
            .thenReturn(List.of());
    when(subjectRelationRepository.findBySubjectTypeAndSubjectId(
                    ArchiveDataScopeSubjectType.DEPARTMENT, 3L))
            .thenReturn(List.of(subjectScope(ArchiveDataScopeSubjectType.DEPARTMENT, 3L, 100L)));
    when(userRoleRelationRepository.findByUserId(7L)).thenReturn(List.of());
    when(dataScopeRepository.findById(100L))
            .thenReturn(Optional.of(scope(100L, ArchiveDataScopeType.CONDITIONAL, true)));
    ArchiveDataScopeDimension dimension = new ArchiveDataScopeDimension();
    dimension.setDimensionType(ArchiveDataScopeDimensionType.DEPARTMENT);
    dimension.setTargetId(3L);
    when(dimensionRepository.findByScopeId(100L)).thenReturn(List.of(dimension));

    ArchiveDataScopeService.ResolvedArchiveDataScope resolved =
            dataScopeService.resolveUserDataScope(7L);

    assertThat(resolved.empty()).isFalse();
    assertThat(resolved.scopes()).hasSize(1);
}

@Test
@DisplayName("停用所属部门不参与用户范围计算")
void resolveUserScopeShouldIgnoreDisabledDepartmentScopes() {
    AuthenticationUser user = new AuthenticationUser();
    user.setId(7L);
    user.setDepartmentId(3L);
    user.setEnabled(true);
    when(authenticationUserRepository.findById(7L)).thenReturn(Optional.of(user));
    when(departmentService.getDepartment(3L))
            .thenReturn(department(3L, false));
    when(subjectRelationRepository.findBySubjectTypeAndSubjectId(
                    ArchiveDataScopeSubjectType.USER, 7L))
            .thenReturn(List.of());
    when(userRoleRelationRepository.findByUserId(7L)).thenReturn(List.of());

    ArchiveDataScopeService.ResolvedArchiveDataScope resolved =
            dataScopeService.resolveUserDataScope(7L);

    assertThat(resolved.empty()).isTrue();
}
```

Add helper:

```java
private static OrganizationDepartmentResponse department(Long id, boolean enabled) {
    return new OrganizationDepartmentResponse(
            id, "DA", "档案部", null, enabled, 0, LocalDateTime.now(), LocalDateTime.now());
}
```

- [ ] **Step 2: Run tests to verify failure**

Run:

```bash
cd server && mvn -q -Dtest=ArchiveDataScopeServiceTests test
```

Expected: FAIL because department subject scopes are not appended in `resolveUserDataScope`.

- [ ] **Step 3: Implement department subject scope append**

In `ArchiveDataScopeService.resolveUserDataScope`, after user direct scope and before role scope, add:

```java
AuthenticationUser user = authenticationUserRepository.findById(userId).orElse(null);
if (user != null && user.getDepartmentId() != null) {
    OrganizationDepartmentResponse department = departmentService.getDepartment(user.getDepartmentId());
    if (department.enabled()
            && appendSubjectScopes(
                    ArchiveDataScopeSubjectType.DEPARTMENT, department.id(), scopes)) {
        return ResolvedArchiveDataScope.all();
    }
}
```

Ensure `getDepartment` does not reject disabled departments; only `requireEnabledDepartment` rejects disabled departments.

- [ ] **Step 4: Run data scope tests**

Run:

```bash
cd server && mvn -q -Dtest=ArchiveDataScopeServiceTests test
```

Expected: PASS.

- [ ] **Step 5: Commit runtime department scopes**

```bash
git add server/src/main/java/github/luckygc/am/module/archive/authorization/service/ArchiveDataScopeService.java \
  server/src/test/java/github/luckygc/am/module/archive/authorization/service/ArchiveDataScopeServiceTests.java
git commit -m "feat: include department data scopes"
```

---

### Task 7: Frontend Department API and Organization Page

**Files:**
- Modify: `web/src/shared/types/archive.ts`
- Modify: `web/src/shared/api/archive.ts`
- Create: `web/src/pages/organization-departments/OrganizationDepartmentsPage.tsx`
- Create: `web/src/pages/organization-departments/OrganizationDepartmentsPage.test.tsx`
- Modify: `web/src/app/routes.tsx`
- Modify: `web/src/layout/AppShell.tsx`

- [ ] **Step 1: Write failing organization page test**

Create `web/src/pages/organization-departments/OrganizationDepartmentsPage.test.tsx`:

```tsx
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vite-plus/test";

import { OrganizationDepartmentsPage } from "./OrganizationDepartmentsPage";

const archiveApiMocks = vi.hoisted(() => ({
    createOrganizationDepartment: vi.fn(),
    listOrganizationDepartments: vi.fn(),
    updateOrganizationDepartment: vi.fn(),
}));

vi.mock("@/shared/api/archive", async (importOriginal) => {
    const actual = await importOriginal<typeof import("@/shared/api/archive")>();
    return {
        ...actual,
        ...archiveApiMocks,
    };
});

beforeEach(() => {
    archiveApiMocks.listOrganizationDepartments.mockResolvedValue({
        items: [
            {
                id: 1,
                departmentCode: "DA",
                departmentName: "档案部",
                enabled: true,
                sortOrder: 0,
                createdAt: "2026-07-03T00:00:00",
                updatedAt: "2026-07-03T00:00:00",
            },
        ],
    });
    archiveApiMocks.createOrganizationDepartment.mockResolvedValue({
        id: 2,
        departmentCode: "RS",
        departmentName: "人事部",
        enabled: true,
        sortOrder: 0,
        createdAt: "2026-07-03T00:00:00",
        updatedAt: "2026-07-03T00:00:00",
    });
});

afterEach(() => {
    cleanup();
    vi.clearAllMocks();
});

describe("OrganizationDepartmentsPage", () => {
    it("renders departments and creates a root department", async () => {
        render(
            <QueryClientProvider client={new QueryClient()}>
                <OrganizationDepartmentsPage />
            </QueryClientProvider>,
        );

        expect(await screen.findByText("DA 档案部")).toBeInTheDocument();

        fireEvent.click(screen.getByRole("button", { name: "新建根部门" }));
        fireEvent.change(screen.getByLabelText("部门编码"), { target: { value: "RS" } });
        fireEvent.change(screen.getByLabelText("部门名称"), { target: { value: "人事部" } });
        fireEvent.click(screen.getByRole("button", { name: "保存" }));

        await waitFor(() => {
            expect(archiveApiMocks.createOrganizationDepartment).toHaveBeenCalledWith({
                departmentCode: "RS",
                departmentName: "人事部",
                enabled: true,
                parentId: undefined,
                sortOrder: 0,
            });
        });
    });
});
```

- [ ] **Step 2: Run frontend test to verify failure**

Run:

```bash
cd web && vp test run src/pages/organization-departments/OrganizationDepartmentsPage.test.tsx
```

Expected: FAIL because the page and API functions do not exist.

- [ ] **Step 3: Add frontend types and API functions**

In `web/src/shared/types/archive.ts`, replace `OrganizationUnitDto` with:

```ts
export interface OrganizationDepartmentDto {
    id: number;
    departmentCode: string;
    departmentName: string;
    parentId?: number;
    enabled: boolean;
    sortOrder: number;
    createdAt: string;
    updatedAt: string;
}

export interface CreateOrganizationDepartmentRequest {
    departmentCode: string;
    departmentName: string;
    parentId?: number;
    enabled?: boolean;
    sortOrder?: number;
}

export interface UpdateOrganizationDepartmentRequest {
    departmentCode?: string;
    departmentName?: string;
    parentId?: number;
    enabled?: boolean;
    sortOrder?: number;
}
```

In `web/src/shared/api/archive.ts`, add imports and functions:

```ts
export function listOrganizationDepartments(enabled?: boolean) {
    return httpClient.get<CollectionResponse<OrganizationDepartmentDto>>(
        `/api/v1/organization-departments${queryString({ enabled })}`,
    );
}

export function createOrganizationDepartment(payload: CreateOrganizationDepartmentRequest) {
    return httpClient.post<OrganizationDepartmentDto>("/api/v1/organization-departments", payload);
}

export function updateOrganizationDepartment(
    id: number,
    payload: UpdateOrganizationDepartmentRequest,
) {
    return httpClient.patch<OrganizationDepartmentDto>(
        `/api/v1/organization-departments/${id}`,
        payload,
    );
}
```

- [ ] **Step 4: Add organization departments page**

Create `OrganizationDepartmentsPage.tsx` with this structure:

```tsx
import { PlusOutlined } from "@ant-design/icons";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Button, Card, Drawer, Form, Input, InputNumber, Space, Switch, Table, Tree, Typography, message } from "antd";
import type { DataNode } from "antd/es/tree";
import type { TableColumnsType } from "antd";
import { useMemo, useState } from "react";

import {
    createOrganizationDepartment,
    listOrganizationDepartments,
    updateOrganizationDepartment,
} from "@/shared/api/archive";
import type {
    CreateOrganizationDepartmentRequest,
    OrganizationDepartmentDto,
} from "@/shared/types/archive";

const queryKey = ["organization-departments"] as const;

export function OrganizationDepartmentsPage() {
    const [form] = Form.useForm<CreateOrganizationDepartmentRequest>();
    const queryClient = useQueryClient();
    const [selectedId, setSelectedId] = useState<number>();
    const [editing, setEditing] = useState<OrganizationDepartmentDto>();
    const [drawerOpen, setDrawerOpen] = useState(false);

    const departmentsQuery = useQuery({
        queryKey,
        queryFn: () => listOrganizationDepartments(false),
    });

    const departments = departmentsQuery.data?.items ?? [];
    const selected = departments.find((item) => item.id === selectedId) ?? departments[0];
    const treeData = useMemo(() => buildTree(departments), [departments]);
    const children = selected ? departments.filter((item) => item.parentId === selected.id) : [];

    const saveMutation = useMutation({
        mutationFn: (values: CreateOrganizationDepartmentRequest) =>
            editing
                ? updateOrganizationDepartment(editing.id, values)
                : createOrganizationDepartment(values),
        onSuccess: async () => {
            await queryClient.invalidateQueries({ queryKey });
            setDrawerOpen(false);
            setEditing(undefined);
            form.resetFields();
            void message.success("部门已保存");
        },
    });

    const columns: TableColumnsType<OrganizationDepartmentDto> = [
        { title: "部门编码", dataIndex: "departmentCode", width: 140 },
        { title: "部门名称", dataIndex: "departmentName" },
        { title: "排序", dataIndex: "sortOrder", width: 90 },
        {
            title: "启用",
            dataIndex: "enabled",
            width: 90,
            render: (enabled: boolean, row) => (
                <Switch
                    checked={enabled}
                    size="small"
                    onChange={(checked) =>
                        updateOrganizationDepartment(row.id, { enabled: checked })
                    }
                />
            ),
        },
        {
            title: "操作",
            width: 100,
            render: (_, row) => (
                <Button size="small" type="link" onClick={() => openEdit(row)}>
                    编辑
                </Button>
            ),
        },
    ];

    function openCreate(parentId?: number) {
        setEditing(undefined);
        form.setFieldsValue({ enabled: true, parentId, sortOrder: 0 });
        setDrawerOpen(true);
    }

    function openEdit(row: OrganizationDepartmentDto) {
        setEditing(row);
        form.setFieldsValue(row);
        setDrawerOpen(true);
    }

    return (
        <section className="am-page">
            <div className="am-page__header">
                <Typography.Title level={1}>组织架构</Typography.Title>
                <Space>
                    <Button icon={<PlusOutlined />} type="primary" onClick={() => openCreate()}>
                        新建根部门
                    </Button>
                    <Button disabled={!selected} onClick={() => openCreate(selected?.id)}>
                        新建下级部门
                    </Button>
                </Space>
            </div>
            <div className="am-split-page">
                <Card className="am-split-page__side">
                    <Tree
                        treeData={treeData}
                        selectedKeys={selected ? [String(selected.id)] : []}
                        onSelect={(keys) => setSelectedId(Number(keys[0]))}
                    />
                </Card>
                <Card className="am-split-page__main">
                    <Table
                        columns={columns}
                        dataSource={children}
                        loading={departmentsQuery.isLoading}
                        pagination={false}
                        rowKey="id"
                    />
                </Card>
            </div>
            <Drawer
                open={drawerOpen}
                title={editing ? "编辑部门" : "新建部门"}
                width={520}
                onClose={() => setDrawerOpen(false)}
                extra={
                    <Button loading={saveMutation.isPending} type="primary" onClick={() => form.submit()}>
                        保存
                    </Button>
                }
            >
                <Form form={form} layout="vertical" onFinish={(values) => saveMutation.mutate(values)}>
                    <Form.Item label="部门编码" name="departmentCode" rules={[{ required: true, message: "请输入部门编码" }]}>
                        <Input />
                    </Form.Item>
                    <Form.Item label="部门名称" name="departmentName" rules={[{ required: true, message: "请输入部门名称" }]}>
                        <Input />
                    </Form.Item>
                    <Form.Item name="parentId" hidden>
                        <InputNumber />
                    </Form.Item>
                    <Form.Item label="排序" name="sortOrder">
                        <InputNumber min={0} style={{ width: "100%" }} />
                    </Form.Item>
                    <Form.Item label="启用" name="enabled" valuePropName="checked">
                        <Switch />
                    </Form.Item>
                </Form>
            </Drawer>
        </section>
    );
}

function buildTree(departments: OrganizationDepartmentDto[]): DataNode[] {
    return departments
        .filter((item) => item.parentId == null)
        .map((item) => toNode(item, departments));
}

function toNode(item: OrganizationDepartmentDto, departments: OrganizationDepartmentDto[]): DataNode {
    return {
        key: String(item.id),
        title: `${item.departmentCode} ${item.departmentName}`,
        children: departments
            .filter((child) => child.parentId === item.id)
            .map((child) => toNode(child, departments)),
    };
}
```

If `am-split-page` CSS classes do not exist, add minimal layout CSS in the existing web stylesheet:

```css
.am-split-page {
    display: grid;
    grid-template-columns: minmax(260px, 320px) minmax(0, 1fr);
    gap: 16px;
}

.am-split-page__side,
.am-split-page__main {
    min-width: 0;
}
```

- [ ] **Step 5: Wire route and menu**

In `web/src/app/routes.tsx`, import page and add route:

```tsx
import { OrganizationDepartmentsPage } from "@/pages/organization-departments/OrganizationDepartmentsPage";
```

Add route under system:

```tsx
{
    path: "system/organization-departments",
    element: <OrganizationDepartmentsPage />,
    handle: {
        title: "组织架构",
        icon: <ApartmentOutlined />,
        isMenu: true,
        keepAlive: true,
    } satisfies AppRouteHandle,
}
```

In `web/src/layout/AppShell.tsx`, add menu item before user management:

```tsx
{
    key: "/system/organization-departments",
    label: <Link to="/system/organization-departments">组织架构</Link>,
    icon: <ApartmentOutlined />,
}
```

- [ ] **Step 6: Run page test**

Run:

```bash
cd web && vp test run src/pages/organization-departments/OrganizationDepartmentsPage.test.tsx
```

Expected: PASS.

- [ ] **Step 7: Commit organization page**

```bash
git add web/src/shared/types/archive.ts web/src/shared/api/archive.ts \
  web/src/pages/organization-departments \
  web/src/app/routes.tsx web/src/layout/AppShell.tsx web/src/**/*.css
git commit -m "feat: add organization departments page"
```

---

### Task 8: Frontend User and Data Scope Department Wiring

**Files:**
- Modify: `web/src/pages/authentication-users/AuthenticationUsersPage.tsx`
- Modify: `web/src/pages/authentication-users/AuthenticationUsersPage.test.tsx`
- Modify: `web/src/pages/archive-data-scopes/ArchiveDataScopesPage.tsx`
- Modify: `web/src/pages/archive-items/ArchiveItemManagementPage.tsx`
- Modify: `web/src/shared/types/archive.ts`
- Modify: `web/src/shared/api/archive.ts`

- [ ] **Step 1: Write failing user department UI test**

Extend `AuthenticationUsersPage.test.tsx` mocks:

```ts
listOrganizationDepartments: vi.fn(),
```

In `beforeEach`:

```ts
archiveApiMocks.listOrganizationDepartments.mockResolvedValue({
    items: [
        {
            id: 3,
            departmentCode: "DA",
            departmentName: "档案部",
            enabled: true,
            sortOrder: 0,
            createdAt: "2026-07-03T00:00:00",
            updatedAt: "2026-07-03T00:00:00",
        },
    ],
});
archiveApiMocks.listAuthenticationUsers.mockResolvedValue({
    items: [
        {
            id: 7,
            username: "zhangsan",
            displayName: "张三",
            departmentId: 3,
            departmentName: "档案部",
            enabled: true,
            createdAt: "2026-07-03T00:00:00",
        },
    ],
});
```

Add test:

```ts
it("shows and edits user department", async () => {
    archiveApiMocks.getAuthenticationUser.mockResolvedValue({
        id: 7,
        username: "zhangsan",
        displayName: "张三",
        departmentId: 3,
        departmentName: "档案部",
        enabled: true,
        createdAt: "2026-07-03T00:00:00",
        roles: [],
    });
    archiveApiMocks.updateAuthenticationUser.mockResolvedValue({
        id: 7,
        username: "zhangsan",
        displayName: "张三",
        departmentId: 3,
        departmentName: "档案部",
        enabled: true,
        createdAt: "2026-07-03T00:00:00",
    });

    render(
        <QueryClientProvider client={new QueryClient()}>
            <AuthenticationUsersPage />
        </QueryClientProvider>,
    );

    expect(await screen.findByText("档案部")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: /编\s*辑/ }));
    await screen.findByLabelText("所属部门");
    fireEvent.click(screen.getByRole("button", { name: "OK" }));

    await waitFor(() => {
        expect(archiveApiMocks.updateAuthenticationUser).toHaveBeenCalledWith(
            7,
            expect.objectContaining({ departmentId: 3 }),
        );
    });
});
```

- [ ] **Step 2: Run test to verify failure**

Run:

```bash
cd web && vp test run src/pages/authentication-users/AuthenticationUsersPage.test.tsx
```

Expected: FAIL because user page does not load or submit departments.

- [ ] **Step 3: Update user types and page**

In `AuthenticationUserDto`, add:

```ts
departmentId?: number;
departmentCode?: string;
departmentName?: string;
```

In create/update request interfaces, add:

```ts
departmentId?: number;
```

In `AuthenticationUsersPage.tsx`, import `listOrganizationDepartments` and query enabled departments:

```tsx
const departmentsQuery = useQuery({
    queryKey: ["organization-departments", "enabled"],
    queryFn: () => listOrganizationDepartments(true),
});
```

Add table column:

```tsx
{
    title: "所属部门",
    dataIndex: "departmentName",
    key: "departmentName",
    width: 160,
    render: (value?: string) => value ?? "",
}
```

Set form field in edit load:

```ts
departmentId: detail.departmentId,
```

Submit create/update:

```ts
departmentId: values.departmentId,
```

Add form item:

```tsx
<Form.Item label="所属部门" name="departmentId">
    <Select
        allowClear
        options={(departmentsQuery.data?.items ?? []).map((department) => ({
            label: `${department.departmentCode} ${department.departmentName}`,
            value: department.id,
        }))}
    />
</Form.Item>
```

- [ ] **Step 4: Update data scope dimension**

In `ArchiveDataScopeDimensionType`, replace `"ORG_UNIT"` with:

```ts
"DEPARTMENT"
```

In `ArchiveDataScopesPage.tsx`, replace `orgUnitIds` with `departmentIds`, label with `部门范围`, and API query with `listOrganizationDepartments(true)`.

Use request mapping:

```ts
...(values.departmentIds ?? []).map((targetId) => ({
    dimensionType: "DEPARTMENT" as const,
    targetId,
    includeDescendants: false,
})),
```

Use form mapping:

```ts
departmentIds: row.dimensions
    .filter((item) => item.dimensionType === "DEPARTMENT")
    .map((item) => item.targetId)
    .filter((item): item is number => typeof item === "number"),
```

- [ ] **Step 5: Update archive item request fields**

In frontend archive item types, replace:

```ts
orgUnitId?: number;
```

with:

```ts
departmentId?: number;
```

In `ArchiveItemManagementPage.tsx`, rename form field and payload field from `orgUnitId` to `departmentId`. Label should be `所属部门`.

- [ ] **Step 6: Run focused frontend tests**

Run:

```bash
cd web && vp test run src/pages/authentication-users/AuthenticationUsersPage.test.tsx src/pages/archive-items/ArchiveItemManagementPage.test.tsx
```

Expected: PASS.

- [ ] **Step 7: Search old frontend names**

Run:

```bash
rg -n "OrganizationUnit|organization-units|ORG_UNIT|orgUnit|unitCode|unitName|组织单元" web/src
```

Expected: no matches.

- [ ] **Step 8: Commit frontend wiring**

```bash
git add web/src/pages/authentication-users web/src/pages/archive-data-scopes \
  web/src/pages/archive-items web/src/shared/types/archive.ts web/src/shared/api/archive.ts
git commit -m "feat: wire departments into user and archive forms"
```

---

### Task 9: Final Verification and Cleanup

**Files:**
- Modify: `openspec/changes/add-organization-departments/tasks.md`
- Modify: any source file found by old-symbol search.

- [ ] **Step 1: Search old names across project-owned source**

Run:

```bash
rg -n "OrganizationUnit|organization-units|am_organization_unit|ORG_UNIT|orgUnit|org_unit|unitCode|unitName|组织单元" \
  server/src/main/java server/src/test/java server/src/main/resources web/src openspec/changes/add-organization-departments
```

Expected: no matches. If matches remain in generated comments, replace with department terminology in the owning source file.

- [ ] **Step 2: Run OpenSpec validation**

Run:

```bash
openspec validate add-organization-departments --strict
```

Expected: PASS.

- [ ] **Step 3: Run backend compile**

Run:

```bash
cd server && mvn -q -DskipTests test-compile
```

Expected: PASS.

- [ ] **Step 4: Run focused backend tests**

Run:

```bash
cd server && mvn -q -Dtest=OrganizationDepartmentServiceTests,AuthorizationPermissionServiceTests,AuthenticationUserManagementServiceTests,ArchiveDataScopeServiceTests,ArchiveMapperDataScopeSqlTests,ArchitectureRulesTest test
```

Expected: PASS.

- [ ] **Step 5: Run frontend checks**

Run:

```bash
cd web && vp check
```

Expected: PASS.

- [ ] **Step 6: Run focused frontend tests**

Run:

```bash
cd web && vp test run src/pages/organization-departments/OrganizationDepartmentsPage.test.tsx src/pages/authentication-users/AuthenticationUsersPage.test.tsx src/pages/archive-items/ArchiveItemManagementPage.test.tsx
```

Expected: PASS.

- [ ] **Step 7: Update task checklist**

Mark all completed items in `openspec/changes/add-organization-departments/tasks.md` with `[x]`.

- [ ] **Step 8: Final commit**

```bash
git add openspec/changes/add-organization-departments/tasks.md
git commit -m "chore: verify organization departments"
```
