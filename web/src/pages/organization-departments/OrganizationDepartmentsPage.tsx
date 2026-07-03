import { PlusOutlined } from "@ant-design/icons";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
    Button,
    Card,
    Form,
    Input,
    InputNumber,
    Modal,
    Select,
    Space,
    Switch,
    Table,
    Tag,
    Typography,
    message,
} from "antd";
import type { TableColumnsType } from "antd";
import type { Key } from "react";
import { useEffect, useMemo, useState } from "react";

import {
    createOrganizationDepartment,
    listOrganizationDepartments,
    updateOrganizationDepartment,
} from "@/shared/api/archive";
import type {
    CreateOrganizationDepartmentRequest,
    OrganizationDepartmentDto,
    UpdateOrganizationDepartmentRequest,
} from "@/shared/types/archive";

type DepartmentFormValues = {
    departmentCode: string;
    departmentName: string;
    parentId?: number;
    enabled: boolean;
    sortOrder: number;
};

type DepartmentTreeNode = OrganizationDepartmentDto & {
    children?: DepartmentTreeNode[];
};

const departmentsQueryKey = ["organization-departments"] as const;
const emptyDepartments: OrganizationDepartmentDto[] = [];

export function OrganizationDepartmentsPage() {
    const [form] = Form.useForm<DepartmentFormValues>();
    const queryClient = useQueryClient();
    const [modalOpen, setModalOpen] = useState(false);
    const [editing, setEditing] = useState<OrganizationDepartmentDto>();
    const [expandedRowKeys, setExpandedRowKeys] = useState<Key[]>([]);

    const departmentsQuery = useQuery({
        queryKey: departmentsQueryKey,
        queryFn: () => listOrganizationDepartments(),
    });
    const departments = departmentsQuery.data?.items ?? emptyDepartments;
    const departmentsById = useMemo(
        () => new Map(departments.map((department) => [department.id, department])),
        [departments],
    );
    const treeData = useMemo(() => buildDepartmentTree(departments), [departments]);
    const excludedParentIds = useMemo(
        () =>
            editing ? descendantIds(departments, editing.id).add(editing.id) : new Set<number>(),
        [departments, editing],
    );

    useEffect(() => {
        const parentIds = new Set(
            departments
                .map((department) => department.parentId)
                .filter((parentId): parentId is number => typeof parentId === "number"),
        );
        setExpandedRowKeys([...parentIds]);
    }, [departments]);

    const createMutation = useMutation({
        mutationFn: (payload: CreateOrganizationDepartmentRequest) =>
            createOrganizationDepartment(payload),
        onSuccess: async () => {
            await queryClient.invalidateQueries({ queryKey: departmentsQueryKey });
            closeModal();
            void message.success("部门已创建");
        },
        onError: (error: Error) => message.error(error.message),
    });

    const updateMutation = useMutation({
        mutationFn: ({
            id,
            payload,
        }: {
            id: number;
            payload: UpdateOrganizationDepartmentRequest;
        }) => updateOrganizationDepartment(id, payload),
        onSuccess: async () => {
            await queryClient.invalidateQueries({ queryKey: departmentsQueryKey });
            closeModal();
            void message.success("部门已保存");
        },
        onError: (error: Error) => message.error(error.message),
    });

    const columns: TableColumnsType<DepartmentTreeNode> = [
        { title: "部门编码", dataIndex: "departmentCode", key: "departmentCode", width: 160 },
        { title: "部门名称", dataIndex: "departmentName", key: "departmentName" },
        {
            title: "上级部门",
            dataIndex: "parentId",
            key: "parentId",
            width: 180,
            render: (parentId?: number) =>
                parentId ? (departmentsById.get(parentId)?.departmentName ?? "-") : "-",
        },
        { title: "排序", dataIndex: "sortOrder", key: "sortOrder", width: 90 },
        {
            title: "状态",
            dataIndex: "enabled",
            key: "enabled",
            width: 90,
            render: (enabled: boolean) =>
                enabled ? <Tag color="success">启用</Tag> : <Tag>停用</Tag>,
        },
        {
            title: "操作",
            key: "actions",
            width: 180,
            render: (_, row) => (
                <Space size="small">
                    <Button size="small" onClick={() => openEdit(row)}>
                        编辑
                    </Button>
                    <Button size="small" type="link" onClick={() => openCreate(row.id)}>
                        新增下级
                    </Button>
                </Space>
            ),
        },
    ];

    function openCreate(parentId?: number) {
        setEditing(undefined);
        form.resetFields();
        form.setFieldsValue({
            parentId,
            enabled: true,
            sortOrder: 0,
        });
        setModalOpen(true);
    }

    function openEdit(department: OrganizationDepartmentDto) {
        setEditing(department);
        form.resetFields();
        form.setFieldsValue({
            departmentCode: department.departmentCode,
            departmentName: department.departmentName,
            parentId: department.parentId,
            enabled: department.enabled,
            sortOrder: department.sortOrder,
        });
        setModalOpen(true);
    }

    function closeModal() {
        setModalOpen(false);
        setEditing(undefined);
        form.resetFields();
    }

    async function submit() {
        const values = await form.validateFields();
        if (editing) {
            updateMutation.mutate({
                id: editing.id,
                payload: {
                    departmentCode: values.departmentCode,
                    departmentName: values.departmentName,
                    parentId: values.parentId ?? null,
                    enabled: values.enabled,
                    sortOrder: values.sortOrder ?? 0,
                },
            });
            return;
        }
        createMutation.mutate({
            departmentCode: values.departmentCode,
            departmentName: values.departmentName,
            parentId: values.parentId,
            enabled: values.enabled,
            sortOrder: values.sortOrder ?? 0,
        });
    }

    return (
        <section className="am-page">
            <div className="am-page__header">
                <Typography.Title level={1}>组织架构</Typography.Title>
                <Button
                    aria-label="新建部门"
                    icon={<PlusOutlined />}
                    type="primary"
                    onClick={() => openCreate()}
                >
                    新建部门
                </Button>
            </div>
            <Card>
                <Table<DepartmentTreeNode>
                    columns={columns}
                    dataSource={treeData}
                    expandable={{
                        expandedRowKeys,
                        onExpandedRowsChange: (keys) => setExpandedRowKeys([...keys]),
                    }}
                    loading={departmentsQuery.isLoading}
                    pagination={false}
                    rowKey="id"
                    locale={{ emptyText: "暂无部门" }}
                />
            </Card>
            <Modal
                destroyOnHidden
                okText="保存"
                open={modalOpen}
                title={editing ? "编辑部门" : "新建部门"}
                confirmLoading={createMutation.isPending || updateMutation.isPending}
                onCancel={closeModal}
                onOk={submit}
            >
                <Form<DepartmentFormValues> form={form} layout="vertical">
                    <Form.Item
                        label="部门编码"
                        name="departmentCode"
                        rules={[{ required: true, message: "请输入部门编码" }]}
                    >
                        <Input placeholder="例如：D003" />
                    </Form.Item>
                    <Form.Item
                        label="部门名称"
                        name="departmentName"
                        rules={[{ required: true, message: "请输入部门名称" }]}
                    >
                        <Input placeholder="例如：法务部" />
                    </Form.Item>
                    <Form.Item label="上级部门" name="parentId">
                        <Select
                            allowClear
                            showSearch
                            optionFilterProp="label"
                            placeholder="选择上级部门"
                            options={departments.map((department) => ({
                                disabled: excludedParentIds.has(department.id),
                                label: `${department.departmentCode} ${department.departmentName}`,
                                value: department.id,
                            }))}
                        />
                    </Form.Item>
                    <Form.Item label="排序" name="sortOrder">
                        <InputNumber min={0} precision={0} style={{ width: "100%" }} />
                    </Form.Item>
                    <Form.Item label="启用" name="enabled" valuePropName="checked">
                        <Switch checkedChildren="启用" unCheckedChildren="停用" />
                    </Form.Item>
                </Form>
            </Modal>
        </section>
    );
}

function buildDepartmentTree(departments: OrganizationDepartmentDto[]) {
    const childrenByParent = new Map<number | null, OrganizationDepartmentDto[]>();
    for (const department of departments) {
        const parentId = department.parentId ?? null;
        const siblings = childrenByParent.get(parentId) ?? [];
        siblings.push(department);
        childrenByParent.set(parentId, siblings);
    }
    for (const siblings of childrenByParent.values()) {
        siblings.sort((left, right) => left.sortOrder - right.sortOrder || left.id - right.id);
    }
    const build = (parentId: number | null): DepartmentTreeNode[] =>
        (childrenByParent.get(parentId) ?? []).map((department) => {
            const children = build(department.id);
            return {
                ...department,
                children: children.length > 0 ? children : undefined,
            };
        });
    return build(null);
}

function descendantIds(departments: OrganizationDepartmentDto[], id: number) {
    const descendants = new Set<number>();
    let changed = true;
    while (changed) {
        changed = false;
        for (const department of departments) {
            const parentId = department.parentId;
            if (
                parentId != null &&
                (parentId === id || descendants.has(parentId)) &&
                !descendants.has(department.id)
            ) {
                descendants.add(department.id);
                changed = true;
            }
        }
    }
    return descendants;
}
