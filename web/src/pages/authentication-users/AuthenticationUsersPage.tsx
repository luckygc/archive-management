import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
    Alert,
    Button,
    Form,
    Input,
    message,
    Modal,
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
    createAuthenticationUser,
    getAuthenticationUser,
    listAuthenticationUsers,
    listAuthorizationRoles,
    listOrganizationDepartments,
    resetAuthenticationUserPassword,
    saveAuthenticationUserRoles,
    updateAuthenticationUser,
} from "@/shared/api/archive";
import type {
    AuthenticationUserDto,
    AuthorizationRoleDto,
    RoleSummaryDto,
    SaveUserRolesRequest,
} from "@/shared/types/archive";

const usersQueryKey = ["authentication-users"] as const;

export function AuthenticationUsersPage() {
    const queryClient = useQueryClient();
    const [keyword, setKeyword] = useState<string>("");
    const [modalOpen, setModalOpen] = useState(false);
    const [modalMode, setModalMode] = useState<"create" | "edit">("create");
    const [roleModalUserId, setRoleModalUserId] = useState<number | null>(null);
    const [pwModalUserId, setPwModalUserId] = useState<number | null>(null);
    const [editingUserId, setEditingUserId] = useState<number | null>(null);
    const [editUserLoading, setEditUserLoading] = useState(false);
    const [roleLoading, setRoleLoading] = useState(false);
    const [roleLoadFailed, setRoleLoadFailed] = useState(false);
    const [form] = Form.useForm();
    const [pwForm] = Form.useForm();
    const [cursor, setCursor] = useState<string | undefined>();
    const [prevCursor, setPrevCursor] = useState<string | null>(null);
    const [nextCursor, setNextCursor] = useState<string | null>(null);

    const usersQuery = useQuery({
        queryKey: [...usersQueryKey, keyword, cursor],
        queryFn: () => listAuthenticationUsers(keyword || undefined, 100, cursor),
        placeholderData: (prev) => prev,
    });

    const rolesQuery = useQuery({
        queryKey: ["authorization-roles", true],
        queryFn: () => listAuthorizationRoles(true, 1000),
    });

    const departmentsQuery = useQuery({
        queryKey: ["organization-departments", true],
        queryFn: () => listOrganizationDepartments(true),
    });

    const createMutation = useMutation({
        mutationFn: createAuthenticationUser,
        onSuccess: () => {
            message.success("用户创建成功");
            queryClient.invalidateQueries({ queryKey: usersQueryKey });
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
            payload: Parameters<typeof updateAuthenticationUser>[1];
        }) => updateAuthenticationUser(id, payload),
        onSuccess: () => {
            message.success("用户更新成功");
            queryClient.invalidateQueries({ queryKey: usersQueryKey });
            handleModalClose();
        },
        onError: (e: Error) => message.error(e.message),
    });

    const resetPwMutation = useMutation({
        mutationFn: ({ id, newPassword }: { id: number; newPassword: string }) =>
            resetAuthenticationUserPassword(id, { newPassword }),
        onSuccess: () => {
            message.success("密码已重置");
            setPwModalUserId(null);
            pwForm.resetFields();
        },
        onError: (e: Error) => message.error(e.message),
    });

    const [selectedRoleIds, setSelectedRoleIds] = useState<number[]>([]);
    const saveRolesMutation = useMutation({
        mutationFn: ({ id, payload }: { id: number; payload: SaveUserRolesRequest }) =>
            saveAuthenticationUserRoles(id, payload),
        onSuccess: () => {
            message.success("角色分配已保存");
            setRoleModalUserId(null);
            queryClient.invalidateQueries({ queryKey: usersQueryKey });
        },
        onError: (e: Error) => message.error(e.message),
    });

    function handleModalClose() {
        setModalOpen(false);
        setEditingUserId(null);
        form.resetFields();
    }

    function openCreateModal() {
        setModalMode("create");
        setEditingUserId(null);
        form.resetFields();
        setModalOpen(true);
    }

    // 先打开 Modal，等表单字段挂载后再回填编辑数据
    useEffect(() => {
        if (!modalOpen || modalMode !== "edit" || editingUserId == null) {
            return;
        }
        let cancelled = false;
        setEditUserLoading(true);
        async function load() {
            try {
                const detail = await getAuthenticationUser(editingUserId!);
                if (cancelled) return;
                form.setFieldsValue({
                    displayName: detail.displayName,
                    email: detail.email ?? "",
                    mobilePhone: detail.mobilePhone ?? "",
                    departmentId: detail.departmentId,
                    enabled: detail.enabled,
                });
            } finally {
                if (!cancelled) {
                    setEditUserLoading(false);
                }
            }
        }
        load();
        return () => {
            cancelled = true;
        };
    }, [modalOpen, modalMode, editingUserId, form]);

    function openEditModal(user: AuthenticationUserDto) {
        setModalMode("edit");
        setEditingUserId(user.id);
        setModalOpen(true);
    }

    async function handleSubmit() {
        const values = await form.validateFields();
        if (modalMode === "create") {
            createMutation.mutate({
                username: values.username,
                password: values.password,
                displayName: values.displayName,
                email: values.email || undefined,
                mobilePhone: values.mobilePhone || undefined,
                departmentId: values.departmentId ?? undefined,
            });
        } else if (editingUserId != null) {
            updateMutation.mutate({
                id: editingUserId,
                payload: {
                    displayName: values.displayName,
                    email: values.email ?? null,
                    mobilePhone: values.mobilePhone ?? null,
                    departmentId: values.departmentId ?? null,
                    enabled: values.enabled,
                },
            });
        }
    }

    async function handleResetPassword() {
        const values = await pwForm.validateFields();
        if (pwModalUserId != null) {
            resetPwMutation.mutate({
                id: pwModalUserId,
                newPassword: values.newPassword,
            });
        }
    }

    async function openRoleModal(userId: number) {
        setRoleModalUserId(userId);
        setRoleLoading(true);
        setRoleLoadFailed(false);
        setSelectedRoleIds([]);
        try {
            const detail = await getAuthenticationUser(userId);
            setSelectedRoleIds(detail.roles.map((r: RoleSummaryDto) => r.id));
        } catch {
            setRoleLoadFailed(true);
            setSelectedRoleIds([]);
            message.error("用户角色加载失败，请重试");
        } finally {
            setRoleLoading(false);
        }
    }

    function handleSaveRoles() {
        if (roleModalUserId != null && !roleLoading && !roleLoadFailed) {
            saveRolesMutation.mutate({
                id: roleModalUserId,
                payload: { roleIds: selectedRoleIds },
            });
        }
    }

    const users = usersQuery.data?.items ?? [];
    const allRoles = rolesQuery.data?.items ?? [];
    const departments = departmentsQuery.data?.items ?? [];
    const departmentOptions = departments.map((department) => ({
        label: `${department.departmentCode} ${department.departmentName}`,
        value: department.id,
    }));

    // 跟踪分页游标
    useEffect(() => {
        if (usersQuery.data) {
            setPrevCursor(usersQuery.data.prev ?? null);
            setNextCursor(usersQuery.data.next ?? null);
        }
    }, [usersQuery.data]);

    const columns: TableColumnsType<AuthenticationUserDto> = [
        { title: "登录名", dataIndex: "username", key: "username", width: 140 },
        { title: "姓名", dataIndex: "displayName", key: "displayName", width: 140 },
        {
            title: "邮箱",
            dataIndex: "email",
            key: "email",
            width: 180,
            render: (v?: string) => v ?? "-",
        },
        {
            title: "手机号",
            dataIndex: "mobilePhone",
            key: "mobilePhone",
            width: 130,
            render: (v?: string) => v ?? "-",
        },
        {
            title: "所属部门",
            dataIndex: "departmentName",
            key: "departmentName",
            width: 160,
            render: (_: string | undefined, row) =>
                row.departmentName
                    ? `${row.departmentCode ? `${row.departmentCode} ` : ""}${row.departmentName}`
                    : "-",
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
            width: 240,
            render: (_, record) => (
                <Space size="small" wrap>
                    <Button size="small" onClick={() => openEditModal(record)}>
                        编辑
                    </Button>
                    <Button size="small" onClick={() => openRoleModal(record.id)}>
                        角色
                    </Button>
                    <Button
                        size="small"
                        onClick={() => {
                            setPwModalUserId(record.id);
                            pwForm.resetFields();
                        }}
                    >
                        重置密码
                    </Button>
                </Space>
            ),
        },
    ];

    return (
        <section className="am-page">
            <div className="am-page__header">
                <Typography.Title level={1}>用户管理</Typography.Title>
                <Space>
                    <Input.Search
                        placeholder="搜索用户名或显示名称"
                        allowClear
                        onSearch={(v) => {
                            setKeyword(v);
                            setCursor(undefined);
                        }}
                        onChange={(e) => {
                            if (!e.target.value) {
                                setKeyword("");
                                setCursor(undefined);
                            }
                        }}
                        style={{ width: 260 }}
                    />
                    <Button type="primary" onClick={openCreateModal}>
                        新建用户
                    </Button>
                </Space>
            </div>

            <Table<AuthenticationUserDto>
                columns={columns}
                dataSource={users}
                loading={usersQuery.isLoading}
                pagination={false}
                rowKey="id"
                locale={{ emptyText: "暂无用户" }}
            />
            {(prevCursor || nextCursor) && (
                <Space style={{ marginTop: 12 }}>
                    <Button
                        disabled={!prevCursor}
                        loading={usersQuery.isFetching}
                        onClick={() => setCursor(prevCursor ?? undefined)}
                    >
                        上一页
                    </Button>
                    <Button
                        disabled={!nextCursor}
                        loading={usersQuery.isFetching}
                        onClick={() => setCursor(nextCursor ?? undefined)}
                    >
                        下一页
                    </Button>
                </Space>
            )}

            {/* 新建 / 编辑用户 */}
            <Modal
                title={modalMode === "create" ? "新建用户" : "编辑用户"}
                open={modalOpen}
                onOk={handleSubmit}
                onCancel={handleModalClose}
                confirmLoading={createMutation.isPending || updateMutation.isPending}
                destroyOnHidden
            >
                <Spin spinning={editUserLoading}>
                    <Form form={form} layout="vertical">
                        {modalMode === "create" && (
                            <>
                                <Form.Item
                                    name="username"
                                    label="登录名"
                                    rules={[
                                        {
                                            required: true,
                                            whitespace: true,
                                            message: "请输入登录名",
                                        },
                                    ]}
                                >
                                    <Input placeholder="例如：zhangsan" />
                                </Form.Item>
                                <Form.Item
                                    name="password"
                                    label="密码"
                                    rules={[{ required: true, message: "请输入密码" }]}
                                >
                                    <Input.Password placeholder="输入密码" />
                                </Form.Item>
                            </>
                        )}
                        <Form.Item
                            name="displayName"
                            label="显示名称"
                            rules={[
                                {
                                    required: true,
                                    whitespace: true,
                                    message: "请输入显示名称",
                                },
                            ]}
                        >
                            <Input placeholder="例如：张三" />
                        </Form.Item>
                        <Form.Item name="email" label="邮箱">
                            <Input placeholder="zhangsan@example.com" />
                        </Form.Item>
                        <Form.Item name="mobilePhone" label="手机号">
                            <Input placeholder="13800138000" />
                        </Form.Item>
                        <Form.Item name="departmentId" label="所属部门">
                            <Select
                                allowClear
                                showSearch={{ optionFilterProp: "label" }}
                                loading={departmentsQuery.isLoading}
                                options={departmentOptions}
                                placeholder="选择所属部门"
                            />
                        </Form.Item>
                        {modalMode === "edit" && (
                            <Form.Item name="enabled" label="启用" valuePropName="checked">
                                <Switch checkedChildren="启用" unCheckedChildren="停用" />
                            </Form.Item>
                        )}
                    </Form>
                </Spin>
            </Modal>

            {/* 分配角色 */}
            <Modal
                title="分配角色"
                open={roleModalUserId != null}
                onOk={handleSaveRoles}
                onCancel={() => {
                    setRoleModalUserId(null);
                    setRoleLoading(false);
                    setRoleLoadFailed(false);
                    setSelectedRoleIds([]);
                }}
                confirmLoading={saveRolesMutation.isPending}
                okButtonProps={{ disabled: roleLoading || roleLoadFailed }}
                destroyOnHidden
            >
                <Spin spinning={roleLoading}>
                    {roleLoadFailed && (
                        <Alert
                            type="error"
                            showIcon
                            title="用户角色加载失败"
                            description="当前角色数据不可信，请关闭弹窗后重试。"
                            style={{ marginBottom: 12 }}
                        />
                    )}
                    <Select
                        mode="multiple"
                        style={{ width: "100%" }}
                        placeholder="选择角色"
                        value={selectedRoleIds}
                        onChange={(v) => setSelectedRoleIds(v)}
                        options={allRoles.map((r: AuthorizationRoleDto) => ({
                            label: r.roleName,
                            value: r.id,
                        }))}
                    />
                </Spin>
            </Modal>

            {/* 重置密码 */}
            <Modal
                title="重置密码"
                open={pwModalUserId != null}
                onOk={handleResetPassword}
                onCancel={() => {
                    setPwModalUserId(null);
                    pwForm.resetFields();
                }}
                confirmLoading={resetPwMutation.isPending}
                destroyOnHidden
            >
                <Form form={pwForm} layout="vertical">
                    <Form.Item
                        name="newPassword"
                        label="新密码"
                        rules={[{ required: true, message: "请输入新密码" }]}
                    >
                        <Input.Password placeholder="输入新密码" />
                    </Form.Item>
                </Form>
            </Modal>
        </section>
    );
}
