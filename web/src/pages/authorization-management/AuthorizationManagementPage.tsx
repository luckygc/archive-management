import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
    Button,
    Card,
    Col,
    Empty,
    message,
    Modal,
    Row,
    Segmented,
    Select,
    Space,
    Spin,
    Table,
    Tag,
    Typography,
} from "antd";
import type { TableColumnsType } from "antd";
import { useState } from "react";

import {
    getAuthenticationUser,
    getDepartmentArchiveDataScopes,
    getRoleArchiveDataScopes,
    getRolePermissions,
    getUserArchiveDataScopes,
    listArchiveDataScopes,
    listAuthenticationUsers,
    listAuthorizationPermissions,
    listAuthorizationRoles,
    listOrganizationDepartments,
    saveDepartmentArchiveDataScopes,
    saveRoleArchiveDataScopes,
    saveRolePermissions,
    saveUserArchiveDataScopes,
} from "@/shared/api/archive";
import type { ArchiveDataScopeDto, AuthorizationPermissionDto } from "@/shared/types/archive";

type SubjectType = "role" | "user" | "department";

// ── 权限编码可读映射 ──

const moduleNameMap: Record<string, string> = {
    archive: "档案",
    authorization: "授权",
    authentication: "认证",
    organization: "组织",
};

const resourceNameMap: Record<string, string> = {
    item: "条目",
    audit: "审计",
    export: "导出",
    metadata: "元数据",
    "data-scope": "数据范围",
    permission: "功能权限",
    role: "角色",
    session: "会话",
    user: "用户",
    department: "部门",
};

const actionNameMap: Record<string, string> = {
    read: "读取",
    create: "创建",
    update: "修改",
    delete: "删除",
    lock: "锁定",
    manage: "管理",
    "download-electronic-file": "下载电子文件",
};

interface ParsedPermission {
    module: string;
    resource: string;
    action: string;
}

function parsePermissionCode(code: string): ParsedPermission {
    const segments = code.split(":");
    const module = segments[0] ?? "";
    const action = segments[segments.length - 1] ?? "";
    // 中间部分 = 去掉首尾后的剩余段
    const resource = segments.slice(1, -1).join(":");
    return { module, resource, action };
}

function moduleName(code: string) {
    return moduleNameMap[code] ?? code;
}

function resourceName(code: string) {
    return resourceNameMap[code] ?? code;
}

function actionName(code: string) {
    return actionNameMap[code] ?? code;
}

export function AuthorizationManagementPage() {
    const queryClient = useQueryClient();
    const [subjectType, setSubjectType] = useState<SubjectType>("role");
    const [selectedRoleId, setSelectedRoleId] = useState<number | null>(null);
    const [selectedUserId, setSelectedUserId] = useState<number | null>(null);
    const [selectedDepartmentId, setSelectedDepartmentId] = useState<number | null>(null);

    // Permission edit modal
    const [permissionModalOpen, setPermissionModalOpen] = useState(false);
    const [editingPermissionCodes, setEditingPermissionCodes] = useState<string[]>([]);

    // Data scope edit modal
    const [scopeModalOpen, setScopeModalOpen] = useState(false);
    const [editingScopeIds, setEditingScopeIds] = useState<number[]>([]);

    const hasSubject =
        (subjectType === "role" && selectedRoleId != null) ||
        (subjectType === "user" && selectedUserId != null) ||
        (subjectType === "department" && selectedDepartmentId != null);

    // ── Lists ──

    const rolesQuery = useQuery({
        queryKey: ["authorization-roles", true],
        queryFn: () => listAuthorizationRoles(true, 1000),
    });

    const usersQuery = useQuery({
        queryKey: ["authentication-users", ""],
        queryFn: () => listAuthenticationUsers(undefined, 1000),
    });

    const departmentsQuery = useQuery({
        queryKey: ["organization-departments", true],
        queryFn: () => listOrganizationDepartments(true),
    });

    const allPermissionsQuery = useQuery({
        queryKey: ["authorization-permissions"],
        queryFn: () => listAuthorizationPermissions(),
        enabled: permissionModalOpen || hasSubject,
    });

    const allScopesQuery = useQuery({
        queryKey: ["archive-data-scopes", false],
        queryFn: () => listArchiveDataScopes(false),
        enabled: scopeModalOpen || hasSubject,
    });

    // ── Subject detail ──

    const rolePermissionsQuery = useQuery({
        queryKey: ["role-permissions", selectedRoleId],
        queryFn: () => getRolePermissions(selectedRoleId!),
        enabled: subjectType === "role" && selectedRoleId != null,
    });

    const roleScopesQuery = useQuery({
        queryKey: ["role-data-scopes", selectedRoleId],
        queryFn: () => getRoleArchiveDataScopes(selectedRoleId!),
        enabled: subjectType === "role" && selectedRoleId != null,
    });

    const userDetailQuery = useQuery({
        queryKey: ["authentication-user", selectedUserId],
        queryFn: () => getAuthenticationUser(selectedUserId!),
        enabled: subjectType === "user" && selectedUserId != null,
    });

    const userScopesQuery = useQuery({
        queryKey: ["user-data-scopes", selectedUserId],
        queryFn: () => getUserArchiveDataScopes(selectedUserId!),
        enabled: subjectType === "user" && selectedUserId != null,
    });

    const departmentScopesQuery = useQuery({
        queryKey: ["department-data-scopes", selectedDepartmentId],
        queryFn: () => getDepartmentArchiveDataScopes(selectedDepartmentId!),
        enabled: subjectType === "department" && selectedDepartmentId != null,
    });

    // User permissions come from their roles — fetch each role's permissions concurrently
    const userPermissionsQuery = useQuery({
        queryKey: ["user-role-permissions", selectedUserId, userDetailQuery.data],
        queryFn: async () => {
            const detail = userDetailQuery.data!;
            const results = await Promise.allSettled(
                detail.roles.map((r) => getRolePermissions(r.id)),
            );
            const codes = new Set<string>();
            for (const result of results) {
                if (result.status === "fulfilled") {
                    for (const code of result.value.permissionCodes) {
                        codes.add(code);
                    }
                }
            }
            return [...codes].sort();
        },
        enabled: subjectType === "user" && selectedUserId != null && !!userDetailQuery.data,
    });

    // ── Save mutations ──

    const saveRolePermsMutation = useMutation({
        mutationFn: ({ roleId, codes }: { roleId: number; codes: string[] }) =>
            saveRolePermissions(roleId, codes),
        onSuccess: () => {
            message.success("功能权限已保存");
            setPermissionModalOpen(false);
            queryClient.invalidateQueries({ queryKey: ["role-permissions", selectedRoleId] });
            queryClient.invalidateQueries({ queryKey: ["user-role-permissions"] });
        },
        onError: (e: Error) => message.error(e.message),
    });

    const saveRoleScopesMutation = useMutation({
        mutationFn: ({ roleId, scopeIds }: { roleId: number; scopeIds: number[] }) =>
            saveRoleArchiveDataScopes(roleId, scopeIds),
        onSuccess: () => {
            message.success("数据范围已保存");
            setScopeModalOpen(false);
            queryClient.invalidateQueries({ queryKey: ["role-data-scopes", selectedRoleId] });
        },
        onError: (e: Error) => message.error(e.message),
    });

    const saveUserScopesMutation = useMutation({
        mutationFn: ({ userId, scopeIds }: { userId: number; scopeIds: number[] }) =>
            saveUserArchiveDataScopes(userId, scopeIds),
        onSuccess: () => {
            message.success("数据范围已保存");
            setScopeModalOpen(false);
            queryClient.invalidateQueries({ queryKey: ["user-data-scopes", selectedUserId] });
        },
        onError: (e: Error) => message.error(e.message),
    });

    const saveDepartmentScopesMutation = useMutation({
        mutationFn: ({ departmentId, scopeIds }: { departmentId: number; scopeIds: number[] }) =>
            saveDepartmentArchiveDataScopes(departmentId, scopeIds),
        onSuccess: () => {
            message.success("数据范围已保存");
            setScopeModalOpen(false);
            queryClient.invalidateQueries({
                queryKey: ["department-data-scopes", selectedDepartmentId],
            });
        },
        onError: (e: Error) => message.error(e.message),
    });

    // ── Derived ──

    const roles = rolesQuery.data?.items ?? [];
    const users = usersQuery.data?.items ?? [];
    const departments = departmentsQuery.data?.items ?? [];
    const selectedRole = selectedRoleId != null ? roles.find((r) => r.id === selectedRoleId) : null;
    const selectedUser = selectedUserId != null ? users.find((u) => u.id === selectedUserId) : null;
    const selectedDepartment =
        selectedDepartmentId != null
            ? departments.find((department) => department.id === selectedDepartmentId)
            : null;

    const isLoadingDetail =
        (subjectType === "role" &&
            selectedRoleId != null &&
            (rolePermissionsQuery.isLoading || roleScopesQuery.isLoading)) ||
        (subjectType === "user" &&
            selectedUserId != null &&
            (userDetailQuery.isLoading ||
                userScopesQuery.isLoading ||
                userPermissionsQuery.isLoading)) ||
        (subjectType === "department" &&
            selectedDepartmentId != null &&
            departmentScopesQuery.isLoading);

    // Permissions display
    const displayPermissionCodes: string[] =
        subjectType === "role"
            ? (rolePermissionsQuery.data?.permissionCodes ?? [])
            : subjectType === "user"
              ? (userPermissionsQuery.data ?? [])
              : [];

    // Data scopes display
    const displayScopeIds: number[] =
        subjectType === "role"
            ? (roleScopesQuery.data?.scopeIds ?? [])
            : subjectType === "user"
              ? (userScopesQuery.data?.scopeIds ?? [])
              : (departmentScopesQuery.data?.scopeIds ?? []);

    // ── Permission name lookup ──
    const allPermissions = allPermissionsQuery.data?.items ?? [];
    const allScopes = allScopesQuery.data?.items ?? [];

    const permColumns: TableColumnsType<AuthorizationPermissionDto> = [
        {
            title: "模块",
            dataIndex: "moduleCode",
            key: "moduleCode",
            width: 80,
            render: (v: string) => moduleName(v),
        },
        {
            title: "资源",
            key: "resource",
            width: 120,
            render: (_, record) =>
                resourceName(parsePermissionCode(record.permissionCode).resource),
        },
        {
            title: "动作",
            key: "action",
            width: 120,
            render: (_, record) => actionName(parsePermissionCode(record.permissionCode).action),
        },
        { title: "权限名称", dataIndex: "permissionName", key: "permissionName" },
        { title: "说明", dataIndex: "description", key: "description" },
    ];

    const scopeColumns: TableColumnsType<ArchiveDataScopeDto> = [
        { title: "范围编码", dataIndex: "scopeCode", key: "scopeCode", width: 160 },
        { title: "范围名称", dataIndex: "scopeName", key: "scopeName" },
        {
            title: "类型",
            dataIndex: "scopeType",
            key: "scopeType",
            width: 100,
            render: (v: string) => (v === "ALL" ? <Tag color="blue">*</Tag> : <Tag>条件</Tag>),
        },
        {
            title: "启用",
            dataIndex: "enabled",
            key: "enabled",
            width: 80,
            render: (enabled: boolean) =>
                enabled ? <Tag color="success">启用</Tag> : <Tag>停用</Tag>,
        },
        {
            title: "说明",
            dataIndex: "description",
            key: "description",
            render: (v?: string) => v ?? "-",
        },
    ];

    function openPermissionEdit() {
        setEditingPermissionCodes([...displayPermissionCodes]);
        setPermissionModalOpen(true);
    }

    function openScopeEdit() {
        setEditingScopeIds([...displayScopeIds]);
        setScopeModalOpen(true);
    }

    function handleSavePermissions() {
        if (subjectType === "role" && selectedRoleId != null) {
            saveRolePermsMutation.mutate({
                roleId: selectedRoleId,
                codes: editingPermissionCodes,
            });
        }
        // Users get permissions via roles, not directly editable here
    }

    function handleSaveScopes() {
        if (subjectType === "role" && selectedRoleId != null) {
            saveRoleScopesMutation.mutate({
                roleId: selectedRoleId,
                scopeIds: editingScopeIds,
            });
        } else if (subjectType === "user" && selectedUserId != null) {
            saveUserScopesMutation.mutate({
                userId: selectedUserId,
                scopeIds: editingScopeIds,
            });
        } else if (subjectType === "department" && selectedDepartmentId != null) {
            saveDepartmentScopesMutation.mutate({
                departmentId: selectedDepartmentId,
                scopeIds: editingScopeIds,
            });
        }
    }

    return (
        <section className="am-page">
            <div className="am-page__header">
                <Typography.Title level={1}>授权管理</Typography.Title>
            </div>

            <Row gutter={[16, 16]}>
                <Col xs={24} md={8}>
                    <Card title="授权主体">
                        <Space orientation="vertical" style={{ width: "100%" }} size="middle">
                            <Segmented
                                block
                                options={[
                                    { label: "角色", value: "role" },
                                    { label: "用户", value: "user" },
                                    { label: "部门", value: "department" },
                                ]}
                                value={subjectType}
                                onChange={(v) => {
                                    setSubjectType(v as SubjectType);
                                    setSelectedRoleId(null);
                                    setSelectedUserId(null);
                                    setSelectedDepartmentId(null);
                                }}
                            />
                            {subjectType === "role" ? (
                                <Select
                                    showSearch={{
                                        filterOption: (input, option) =>
                                            (option?.label as string)
                                                ?.toLowerCase()
                                                .includes(input.toLowerCase()),
                                    }}
                                    allowClear
                                    placeholder="搜索并选择角色"
                                    style={{ width: "100%" }}
                                    loading={rolesQuery.isLoading}
                                    value={selectedRoleId}
                                    onChange={(v) => {
                                        setSelectedRoleId(v ?? null);
                                    }}
                                    options={roles.map((r) => ({
                                        label: r.roleName,
                                        value: r.id,
                                    }))}
                                />
                            ) : subjectType === "user" ? (
                                <Select
                                    showSearch={{
                                        filterOption: (input, option) =>
                                            (option?.label as string)
                                                ?.toLowerCase()
                                                .includes(input.toLowerCase()),
                                    }}
                                    allowClear
                                    placeholder="搜索并选择用户"
                                    style={{ width: "100%" }}
                                    loading={usersQuery.isLoading}
                                    value={selectedUserId}
                                    onChange={(v) => {
                                        setSelectedUserId(v ?? null);
                                    }}
                                    options={users.map((u) => ({
                                        label: `${u.displayName}（${u.username}）`,
                                        value: u.id,
                                    }))}
                                />
                            ) : (
                                <Select
                                    showSearch={{
                                        filterOption: (input, option) =>
                                            (option?.label as string)
                                                ?.toLowerCase()
                                                .includes(input.toLowerCase()),
                                    }}
                                    allowClear
                                    placeholder="搜索并选择部门"
                                    style={{ width: "100%" }}
                                    loading={departmentsQuery.isLoading}
                                    value={selectedDepartmentId}
                                    onChange={(v) => {
                                        setSelectedDepartmentId(v ?? null);
                                    }}
                                    options={departments.map((department) => ({
                                        label: `${department.departmentCode} ${department.departmentName}`,
                                        value: department.id,
                                    }))}
                                />
                            )}
                        </Space>
                    </Card>

                    {/* Subject info */}
                    {selectedRole && (
                        <Card size="small" style={{ marginTop: 12 }}>
                            <Space orientation="vertical">
                                <Typography.Text strong>{selectedRole.roleName}</Typography.Text>
                                <Typography.Text type="secondary">
                                    {selectedRole.description ?? "无说明"}
                                </Typography.Text>
                                <Tag color={selectedRole.enabled ? "green" : "default"}>
                                    {selectedRole.enabled ? "启用" : "停用"}
                                </Tag>
                            </Space>
                        </Card>
                    )}
                    {selectedDepartment && (
                        <Card size="small" style={{ marginTop: 12 }}>
                            <Space orientation="vertical">
                                <Typography.Text strong>
                                    {selectedDepartment.departmentName}
                                </Typography.Text>
                                <Typography.Text type="secondary">
                                    {selectedDepartment.departmentCode}
                                </Typography.Text>
                                <Tag color={selectedDepartment.enabled ? "green" : "default"}>
                                    {selectedDepartment.enabled ? "启用" : "停用"}
                                </Tag>
                            </Space>
                        </Card>
                    )}
                    {selectedUser && (
                        <Card size="small" style={{ marginTop: 12 }}>
                            <Space orientation="vertical">
                                <Typography.Text strong>{selectedUser.displayName}</Typography.Text>
                                <Typography.Text type="secondary">
                                    {selectedUser.username}
                                    {selectedUser.email ? ` · ${selectedUser.email}` : ""}
                                </Typography.Text>
                                <Tag color={selectedUser.enabled ? "green" : "default"}>
                                    {selectedUser.enabled ? "启用" : "停用"}
                                </Tag>
                            </Space>
                        </Card>
                    )}
                </Col>

                <Col xs={24} md={16}>
                    <Spin spinning={isLoadingDetail}>
                        {!hasSubject ? (
                            <Card>
                                <Empty description="请在左侧选择授权主体" />
                            </Card>
                        ) : (
                            <Space orientation="vertical" style={{ width: "100%" }} size="middle">
                                {subjectType !== "department" && (
                                    <Card
                                        title="功能权限"
                                        extra={
                                            subjectType === "role" && (
                                                <Button
                                                    size="small"
                                                    type="primary"
                                                    onClick={openPermissionEdit}
                                                >
                                                    编辑
                                                </Button>
                                            )
                                        }
                                    >
                                        {subjectType === "user" && (
                                            <Typography.Text
                                                type="secondary"
                                                style={{ display: "block", marginBottom: 8 }}
                                            >
                                                用户权限由其角色决定
                                            </Typography.Text>
                                        )}
                                        {displayPermissionCodes.length === 0 ? (
                                            <Empty
                                                description="暂无功能权限"
                                                image={Empty.PRESENTED_IMAGE_SIMPLE}
                                            />
                                        ) : (
                                            <Table<AuthorizationPermissionDto>
                                                columns={permColumns}
                                                dataSource={allPermissions.filter((p) =>
                                                    displayPermissionCodes.includes(
                                                        p.permissionCode,
                                                    ),
                                                )}
                                                pagination={false}
                                                rowKey="permissionCode"
                                                size="small"
                                            />
                                        )}
                                    </Card>
                                )}

                                {/* 数据范围 */}
                                <Card
                                    title="数据范围"
                                    extra={
                                        <Button size="small" type="primary" onClick={openScopeEdit}>
                                            编辑
                                        </Button>
                                    }
                                >
                                    {displayScopeIds.length === 0 ? (
                                        <Empty
                                            description="暂无数据范围"
                                            image={Empty.PRESENTED_IMAGE_SIMPLE}
                                        />
                                    ) : (
                                        <Table<ArchiveDataScopeDto>
                                            columns={scopeColumns}
                                            dataSource={allScopes.filter((s) =>
                                                displayScopeIds.includes(s.id),
                                            )}
                                            pagination={false}
                                            rowKey="id"
                                            size="small"
                                        />
                                    )}
                                </Card>

                                {/* 用户角色 */}
                                {subjectType === "user" && userDetailQuery.data && (
                                    <Card title="所属角色">
                                        {userDetailQuery.data.roles.length === 0 ? (
                                            <Empty
                                                description="未分配角色"
                                                image={Empty.PRESENTED_IMAGE_SIMPLE}
                                            />
                                        ) : (
                                            <Space wrap>
                                                {userDetailQuery.data.roles.map((r) => (
                                                    <Tag key={r.id} color="blue">
                                                        {r.roleName}
                                                    </Tag>
                                                ))}
                                            </Space>
                                        )}
                                    </Card>
                                )}
                            </Space>
                        )}
                    </Spin>
                </Col>
            </Row>

            {/* 编辑功能权限 Modal */}
            <Modal
                title="编辑功能权限"
                open={permissionModalOpen}
                onOk={handleSavePermissions}
                onCancel={() => setPermissionModalOpen(false)}
                confirmLoading={saveRolePermsMutation.isPending}
                destroyOnHidden
                width={640}
            >
                <Spin spinning={allPermissionsQuery.isLoading}>
                    <Select
                        mode="multiple"
                        style={{ width: "100%" }}
                        placeholder="选择功能权限"
                        value={editingPermissionCodes}
                        onChange={(v) => setEditingPermissionCodes(v)}
                        options={allPermissions.map((p) => ({
                            label: `${p.permissionName}（${moduleName(p.moduleCode)} · ${resourceName(parsePermissionCode(p.permissionCode).resource)} · ${actionName(parsePermissionCode(p.permissionCode).action)}）`,
                            value: p.permissionCode,
                        }))}
                    />
                </Spin>
            </Modal>

            {/* 编辑数据范围 Modal */}
            <Modal
                title="编辑数据范围"
                open={scopeModalOpen}
                onOk={handleSaveScopes}
                onCancel={() => setScopeModalOpen(false)}
                confirmLoading={
                    saveRoleScopesMutation.isPending ||
                    saveUserScopesMutation.isPending ||
                    saveDepartmentScopesMutation.isPending
                }
                destroyOnHidden
                width={640}
            >
                <Spin spinning={allScopesQuery.isLoading}>
                    <Select
                        mode="multiple"
                        style={{ width: "100%" }}
                        placeholder="选择数据范围"
                        value={editingScopeIds}
                        onChange={(v) => setEditingScopeIds(v)}
                        options={allScopes.map((s) => ({
                            label: s.scopeName,
                            value: s.id,
                        }))}
                    />
                </Spin>
            </Modal>
        </section>
    );
}
