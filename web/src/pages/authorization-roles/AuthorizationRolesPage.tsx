import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
    Alert,
    Button,
    Form,
    Input,
    message,
    Modal,
    Popconfirm,
    Select,
    Space,
    Spin,
    Switch,
    Table,
    Tag,
    Typography,
} from "antd";
import type { TableColumnsType } from "antd";
import { useEffect, useState } from "react";

import {
    createAuthorizationRole,
    deleteAuthorizationRole,
    getRolePermissions,
    listAuthorizationPermissions,
    listAuthorizationRoles,
    saveRolePermissions,
    updateAuthorizationRole,
} from "@/shared/api/archive";
import type { AuthorizationRoleDto } from "@/shared/types/archive";

const rolesQueryKey = ["authorization-roles"] as const;

export function AuthorizationRolesPage() {
    const queryClient = useQueryClient();
    const [modalOpen, setModalOpen] = useState(false);
    const [modalMode, setModalMode] = useState<"create" | "edit">("create");
    const [editingRoleId, setEditingRoleId] = useState<number | null>(null);
    const [permissionRoleId, setPermissionRoleId] = useState<number | null>(null);
    const [permissionRoleName, setPermissionRoleName] = useState<string>("");
    const [selectedPermissionCodes, setSelectedPermissionCodes] = useState<string[]>([]);
    const [permissionLoading, setPermissionLoading] = useState(false);
    const [permissionLoadFailed, setPermissionLoadFailed] = useState(false);
    const [form] = Form.useForm();
    const [cursor, setCursor] = useState<string | undefined>();
    const [prevCursor, setPrevCursor] = useState<string | null>(null);
    const [nextCursor, setNextCursor] = useState<string | null>(null);

    const rolesQuery = useQuery({
        queryKey: [...rolesQueryKey, cursor],
        queryFn: () => listAuthorizationRoles(undefined, 100, cursor),
        placeholderData: (prev) => prev,
    });

    const permissionsQuery = useQuery({
        queryKey: ["authorization-permissions"],
        queryFn: () => listAuthorizationPermissions(),
        enabled: permissionRoleId != null,
    });

    const createMutation = useMutation({
        mutationFn: createAuthorizationRole,
        onSuccess: () => {
            message.success("角色创建成功");
            queryClient.invalidateQueries({ queryKey: rolesQueryKey });
            handleModalClose();
        },
        onError: (e: Error) => message.error(e.message),
    });

    const updateMutation = useMutation({
        mutationFn: ({
            id,
            payload,
        }: {
            id: number;
            payload: Parameters<typeof updateAuthorizationRole>[1];
        }) => updateAuthorizationRole(id, payload),
        onSuccess: () => {
            message.success("角色更新成功");
            queryClient.invalidateQueries({ queryKey: rolesQueryKey });
            handleModalClose();
        },
        onError: (e: Error) => message.error(e.message),
    });

    const deleteMutation = useMutation({
        mutationFn: (id: number) => deleteAuthorizationRole(id),
        onSuccess: () => {
            message.success("角色已删除");
            queryClient.invalidateQueries({ queryKey: rolesQueryKey });
        },
        onError: (e: Error) => message.error(e.message),
    });

    const savePermissionsMutation = useMutation({
        mutationFn: ({ roleId, permissionCodes }: { roleId: number; permissionCodes: string[] }) =>
            saveRolePermissions(roleId, permissionCodes),
        onSuccess: () => {
            message.success("权限分配已保存");
            setPermissionRoleId(null);
            queryClient.invalidateQueries({ queryKey: rolesQueryKey });
        },
        onError: (e: Error) => message.error(e.message),
    });

    function handleModalClose() {
        setModalOpen(false);
        setEditingRoleId(null);
        form.resetFields();
    }

    function openCreateModal() {
        setModalMode("create");
        setEditingRoleId(null);
        form.resetFields();
        form.setFieldsValue({ enabled: true });
        setModalOpen(true);
    }

    function openEditModal(role: AuthorizationRoleDto) {
        setModalMode("edit");
        setEditingRoleId(role.id);
        form.setFieldsValue({
            roleName: role.roleName,
            description: role.description ?? "",
            enabled: role.enabled,
        });
        setModalOpen(true);
    }

    async function handleSubmit() {
        const values = await form.validateFields();
        if (modalMode === "create") {
            createMutation.mutate({
                roleName: values.roleName,
                description: values.description || undefined,
            });
        } else if (editingRoleId != null) {
            updateMutation.mutate({
                id: editingRoleId,
                payload: {
                    roleName: values.roleName,
                    description: values.description || undefined,
                    enabled: values.enabled,
                },
            });
        }
    }

    async function openPermissionModal(role: AuthorizationRoleDto) {
        setPermissionRoleId(role.id);
        setPermissionRoleName(role.roleName);
        setSelectedPermissionCodes([]);
        setPermissionLoading(true);
        setPermissionLoadFailed(false);
        try {
            const resp = await getRolePermissions(role.id);
            // guard against stale response if modal was closed mid-flight
            setSelectedPermissionCodes(resp.permissionCodes);
        } catch {
            setPermissionLoadFailed(true);
            setSelectedPermissionCodes([]);
            message.error("角色权限加载失败，请重试");
        } finally {
            setPermissionLoading(false);
        }
    }

    function handleSavePermissions() {
        if (permissionRoleId != null && !permissionLoading && !permissionLoadFailed) {
            savePermissionsMutation.mutate({
                roleId: permissionRoleId,
                permissionCodes: selectedPermissionCodes,
            });
        }
    }

    const roles = rolesQuery.data?.items ?? [];
    const allPermissions = permissionsQuery.data?.items ?? [];

    useEffect(() => {
        if (rolesQuery.data) {
            setPrevCursor(rolesQuery.data.prev ?? null);
            setNextCursor(rolesQuery.data.next ?? null);
        }
    }, [rolesQuery.data]);

    const columns: TableColumnsType<AuthorizationRoleDto> = [
        { title: "角色名称", dataIndex: "roleName", key: "roleName", width: 160 },
        {
            title: "说明",
            dataIndex: "description",
            key: "description",
            render: (v?: string) => v ?? "-",
        },
        {
            title: "状态",
            dataIndex: "enabled",
            key: "enabled",
            width: 80,
            render: (v: boolean) => (
                <Tag color={v ? "green" : "default"}>{v ? "启用" : "停用"}</Tag>
            ),
        },
        { title: "创建时间", dataIndex: "createdAt", key: "createdAt", width: 180 },
        {
            title: "操作",
            key: "actions",
            width: 220,
            render: (_, record) => (
                <Space size="small" wrap>
                    <Button size="small" onClick={() => openEditModal(record)}>
                        编辑
                    </Button>
                    <Button size="small" onClick={() => openPermissionModal(record)}>
                        权限
                    </Button>
                    <Popconfirm
                        title="确定删除此角色？"
                        onConfirm={() => deleteMutation.mutate(record.id)}
                        okText="删除"
                        cancelText="取消"
                    >
                        <Button size="small" danger loading={deleteMutation.isPending}>
                            删除
                        </Button>
                    </Popconfirm>
                </Space>
            ),
        },
    ];

    return (
        <section className="am-page">
            <div className="am-page__header">
                <Typography.Title level={1}>角色管理</Typography.Title>
                <Button type="primary" onClick={openCreateModal}>
                    新建角色
                </Button>
            </div>

            <Table<AuthorizationRoleDto>
                columns={columns}
                dataSource={roles}
                loading={rolesQuery.isLoading}
                pagination={false}
                rowKey="id"
                locale={{ emptyText: "暂无角色" }}
            />
            {(prevCursor || nextCursor) && (
                <Space style={{ marginTop: 12 }}>
                    <Button
                        disabled={!prevCursor}
                        loading={rolesQuery.isFetching}
                        onClick={() => setCursor(prevCursor ?? undefined)}
                    >
                        上一页
                    </Button>
                    <Button
                        disabled={!nextCursor}
                        loading={rolesQuery.isFetching}
                        onClick={() => setCursor(nextCursor ?? undefined)}
                    >
                        下一页
                    </Button>
                </Space>
            )}

            {/* 新建 / 编辑角色 */}
            <Modal
                title={modalMode === "create" ? "新建角色" : "编辑角色"}
                open={modalOpen}
                onOk={handleSubmit}
                onCancel={handleModalClose}
                confirmLoading={createMutation.isPending || updateMutation.isPending}
                destroyOnClose
            >
                <Form form={form} layout="vertical">
                    <Form.Item
                        name="roleName"
                        label="角色名称"
                        rules={[{ required: true, message: "请输入角色名称" }]}
                    >
                        <Input placeholder="例如：档案管理员" />
                    </Form.Item>
                    <Form.Item name="description" label="说明">
                        <Input.TextArea rows={3} placeholder="角色说明（可选）" />
                    </Form.Item>
                    <Form.Item name="enabled" label="启用" valuePropName="checked">
                        <Switch checkedChildren="启用" unCheckedChildren="停用" />
                    </Form.Item>
                </Form>
            </Modal>

            {/* 分配权限 */}
            <Modal
                title={`分配权限 — ${permissionRoleName}`}
                open={permissionRoleId != null}
                onOk={handleSavePermissions}
                onCancel={() => {
                    setPermissionRoleId(null);
                    setPermissionLoading(false);
                    setPermissionLoadFailed(false);
                    setSelectedPermissionCodes([]);
                }}
                confirmLoading={savePermissionsMutation.isPending}
                okButtonProps={{ disabled: permissionLoading || permissionLoadFailed }}
                destroyOnClose
                width={640}
            >
                <Spin spinning={permissionLoading || permissionsQuery.isLoading}>
                    {permissionLoadFailed && (
                        <Alert
                            type="error"
                            showIcon
                            title="角色权限加载失败"
                            description="当前权限数据不可信，请关闭弹窗后重试。"
                            style={{ marginBottom: 12 }}
                        />
                    )}
                    <Select
                        mode="multiple"
                        style={{ width: "100%" }}
                        placeholder="选择功能权限"
                        value={selectedPermissionCodes}
                        onChange={(v) => setSelectedPermissionCodes(v)}
                        options={allPermissions.map((p) => ({
                            label: p.permissionName,
                            value: p.permissionCode,
                        }))}
                    />
                </Spin>
            </Modal>
        </section>
    );
}
