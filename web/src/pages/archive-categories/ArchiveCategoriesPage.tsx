import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
    Button,
    Card,
    Col,
    Form,
    Input,
    InputNumber,
    message,
    Modal,
    Popconfirm,
    Row,
    Select,
    Space,
    Switch,
    Table,
    Tag,
    Tree,
    Typography,
} from "antd";
import type { DataNode } from "antd/es/tree";
import type { TableColumnsType } from "antd";
import { useEffect, useState } from "react";

import {
    buildArchiveCategoryTable,
    createArchiveCategory,
    deleteArchiveCategory,
    listArchiveCategories,
    listArchiveFields,
    updateArchiveCategory,
} from "@/shared/api/archive";
import type {
    ArchiveCategoryDto,
    ArchiveFieldDto,
    ArchiveManagementMode,
} from "@/shared/types/archive";

const categoryQueryKey = ["archive-categories"] as const;

const managementModeLabels: Record<ArchiveManagementMode, string> = {
    ITEM_ONLY: "仅允许著录条目",
    VOLUME_ITEM: "可建卷并著录条目",
};

const fieldTypeLabels: Record<string, string> = {
    TEXT: "文本",
    INTEGER: "整数",
    DECIMAL: "小数",
    DATE: "日期",
    DATETIME: "日期时间",
};

const fieldColumns: TableColumnsType<ArchiveFieldDto> = [
    { title: "字段编码", dataIndex: "fieldCode", key: "fieldCode", width: 160 },
    { title: "字段名称", dataIndex: "fieldName", key: "fieldName" },
    {
        title: "字段类型",
        dataIndex: "fieldType",
        key: "fieldType",
        width: 100,
        render: (v: string) => fieldTypeLabels[v] ?? v,
    },
    {
        title: "层级",
        dataIndex: "archiveLevel",
        key: "archiveLevel",
        width: 80,
        render: (v: string) => (v === "VOLUME" ? "案卷" : "条目"),
    },
    {
        title: "列表显示",
        dataIndex: "listVisible",
        key: "listVisible",
        width: 100,
        render: (v: boolean) => (v ? "是" : "否"),
    },
    {
        title: "启用",
        dataIndex: "enabled",
        key: "enabled",
        width: 80,
        render: (v: boolean) => <Tag color={v ? "green" : "default"}>{v ? "启用" : "停用"}</Tag>,
    },
];

function buildCategoryTree(categories: ArchiveCategoryDto[]): DataNode[] {
    const nodeMap = new Map<number, DataNode>();
    const roots: DataNode[] = [];

    for (const cat of categories) {
        nodeMap.set(cat.id, {
            key: cat.id,
            title: `${cat.categoryName}（${cat.categoryCode}）`,
        });
    }

    for (const cat of categories) {
        const node = nodeMap.get(cat.id)!;
        if (cat.parentId != null) {
            const parent = nodeMap.get(cat.parentId);
            if (parent) {
                parent.children = [...(parent.children ?? []), node];
                continue;
            }
        }
        roots.push(node);
    }

    return roots;
}

export function ArchiveCategoriesPage() {
    const queryClient = useQueryClient();
    const [selectedCategoryId, setSelectedCategoryId] = useState<number | null>(null);
    const [modalOpen, setModalOpen] = useState(false);
    const [modalMode, setModalMode] = useState<"create" | "edit">("create");
    const [editingCategory, setEditingCategory] = useState<ArchiveCategoryDto | null>(null);
    const [form] = Form.useForm();

    const categoriesQuery = useQuery({
        queryKey: categoryQueryKey,
        queryFn: () => listArchiveCategories(),
    });

    const fieldsQuery = useQuery({
        queryKey: ["archive-fields", selectedCategoryId],
        queryFn: () => listArchiveFields(selectedCategoryId!),
        enabled: selectedCategoryId != null,
    });

    const createMutation = useMutation({
        mutationFn: createArchiveCategory,
        onSuccess: () => {
            message.success("分类创建成功");
            queryClient.invalidateQueries({ queryKey: categoryQueryKey });
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
            payload: Parameters<typeof updateArchiveCategory>[1];
        }) => updateArchiveCategory(id, payload),
        onSuccess: () => {
            message.success("分类更新成功");
            queryClient.invalidateQueries({ queryKey: categoryQueryKey });
            handleModalClose();
        },
        onError: (e: Error) => message.error(e.message),
    });

    const deleteMutation = useMutation({
        mutationFn: (id: number) => deleteArchiveCategory(id),
        onSuccess: () => {
            message.success("分类已删除");
            if (selectedCategoryId === deleteMutation.variables) {
                setSelectedCategoryId(null);
            }
            queryClient.invalidateQueries({ queryKey: categoryQueryKey });
        },
        onError: (e: Error) => message.error(e.message),
    });

    const buildTableMutation = useMutation({
        mutationFn: (categoryId: number) => buildArchiveCategoryTable(categoryId),
        onSuccess: () => {
            message.success("动态表已生成");
            queryClient.invalidateQueries({ queryKey: categoryQueryKey });
        },
        onError: (e: Error) => message.error(e.message),
    });

    const categories = categoriesQuery.data?.items ?? [];
    const selectedCategory = categories.find((c) => c.id === selectedCategoryId);

    useEffect(() => {
        const exists = categories.some((c) => c.id === selectedCategoryId);
        if (!exists) {
            setSelectedCategoryId(categories.length > 0 ? categories[0].id : null);
        }
    }, [categories, selectedCategoryId]);

    const treeData = buildCategoryTree(categories);

    function handleModalClose() {
        setModalOpen(false);
        setEditingCategory(null);
        form.resetFields();
    }

    function openCreateModal(parentId?: number) {
        setModalMode("create");
        setEditingCategory(null);
        form.resetFields();
        form.setFieldsValue({
            parentId: parentId ?? undefined,
            enabled: true,
            sortOrder: 0,
            managementMode: "ITEM_ONLY",
        });
        setModalOpen(true);
    }

    function openEditModal(category: ArchiveCategoryDto) {
        setModalMode("edit");
        setEditingCategory(category);
        form.setFieldsValue({
            parentId: category.parentId ?? undefined,
            categoryCode: category.categoryCode,
            categoryName: category.categoryName,
            managementMode: category.managementMode,
            enabled: category.enabled,
            sortOrder: category.sortOrder,
        });
        setModalOpen(true);
    }

    async function handleSubmit() {
        const values = await form.validateFields();
        const payload = {
            parentId: values.parentId ?? undefined,
            categoryCode: values.categoryCode,
            categoryName: values.categoryName,
            managementMode: values.managementMode,
            enabled: values.enabled ?? true,
            sortOrder: values.sortOrder ?? 0,
        };
        if (modalMode === "create") {
            createMutation.mutate(payload);
        } else if (editingCategory) {
            updateMutation.mutate({
                id: editingCategory.id,
                payload: payload as Parameters<typeof updateArchiveCategory>[1],
            });
        }
    }

    const hasSelectedCategory = selectedCategory != null;

    return (
        <section className="am-page">
            <div className="am-page__header">
                <Typography.Title level={1}>档案分类</Typography.Title>
                <Space>
                    <Button
                        type="primary"
                        onClick={() => openCreateModal(selectedCategoryId ?? undefined)}
                    >
                        新建分类
                    </Button>
                    {hasSelectedCategory && selectedCategory.tableStatus === "NOT_BUILT" && (
                        <Button
                            loading={buildTableMutation.isPending}
                            onClick={() => buildTableMutation.mutate(selectedCategory.id)}
                        >
                            生成动态表
                        </Button>
                    )}
                </Space>
            </div>
            <Row gutter={[16, 16]}>
                <Col xs={24} lg={7}>
                    <Card title="分类树" loading={categoriesQuery.isLoading}>
                        {treeData.length === 0 && !categoriesQuery.isLoading ? (
                            <Typography.Text type="secondary">暂无分类，请新建</Typography.Text>
                        ) : (
                            <Tree
                                blockNode
                                defaultExpandAll
                                selectedKeys={
                                    selectedCategoryId != null ? [selectedCategoryId] : []
                                }
                                treeData={treeData}
                                onSelect={(keys) => {
                                    if (keys.length > 0) {
                                        setSelectedCategoryId(keys[0] as number);
                                    }
                                }}
                            />
                        )}
                    </Card>
                </Col>
                <Col xs={24} lg={17}>
                    <Card
                        title={
                            hasSelectedCategory
                                ? `${selectedCategory.categoryName}（${selectedCategory.categoryCode}）`
                                : "字段定义"
                        }
                        loading={fieldsQuery.isLoading}
                        extra={
                            hasSelectedCategory && (
                                <Space wrap>
                                    <Typography.Text type="secondary">
                                        {managementModeLabels[selectedCategory.managementMode]}
                                    </Typography.Text>
                                    {selectedCategory.tableStatus === "BUILT" &&
                                        selectedCategory.itemTableName && (
                                            <Typography.Text code>
                                                {selectedCategory.itemTableName}
                                            </Typography.Text>
                                        )}
                                    {selectedCategory.tableStatus === "BUILT" &&
                                        selectedCategory.volumeTableName && (
                                            <Typography.Text code>
                                                {selectedCategory.volumeTableName}
                                            </Typography.Text>
                                        )}
                                    <Button
                                        size="small"
                                        onClick={() => openEditModal(selectedCategory)}
                                    >
                                        编辑
                                    </Button>
                                    <Popconfirm
                                        title="确定删除此分类？"
                                        onConfirm={() => deleteMutation.mutate(selectedCategory.id)}
                                        okText="删除"
                                        cancelText="取消"
                                    >
                                        <Button
                                            size="small"
                                            danger
                                            loading={deleteMutation.isPending}
                                        >
                                            删除
                                        </Button>
                                    </Popconfirm>
                                </Space>
                            )
                        }
                    >
                        <Table<ArchiveFieldDto>
                            columns={fieldColumns}
                            dataSource={fieldsQuery.data?.items ?? []}
                            loading={fieldsQuery.isLoading}
                            pagination={false}
                            rowKey="id"
                            locale={{
                                emptyText: hasSelectedCategory ? "暂无字段" : "请在左侧选择分类",
                            }}
                        />
                    </Card>
                </Col>
            </Row>

            <Modal
                title={modalMode === "create" ? "新建分类" : "编辑分类"}
                open={modalOpen}
                onOk={handleSubmit}
                onCancel={handleModalClose}
                confirmLoading={createMutation.isPending || updateMutation.isPending}
                destroyOnClose
            >
                <Form form={form} layout="vertical" preserve={false}>
                    <Form.Item name="parentId" label="父级分类">
                        <Select
                            allowClear
                            placeholder="留空则为根分类"
                            options={categories
                                .filter(
                                    (c) => editingCategory == null || c.id !== editingCategory.id,
                                )
                                .map((c) => ({
                                    label: `${c.categoryName}（${c.categoryCode}）`,
                                    value: c.id,
                                }))}
                        />
                    </Form.Item>
                    <Form.Item
                        name="categoryCode"
                        label="分类编码"
                        rules={[{ required: true, message: "请输入分类编码" }]}
                    >
                        <Input placeholder="例如：project" />
                    </Form.Item>
                    <Form.Item
                        name="categoryName"
                        label="分类名称"
                        rules={[{ required: true, message: "请输入分类名称" }]}
                    >
                        <Input placeholder="例如：项目档案" />
                    </Form.Item>
                    <Form.Item name="managementMode" label="管理模式" rules={[{ required: true }]}>
                        <Select
                            options={Object.entries(managementModeLabels).map(([value, label]) => ({
                                value,
                                label,
                            }))}
                        />
                    </Form.Item>
                    <Row gutter={16}>
                        <Col span={12}>
                            <Form.Item name="sortOrder" label="排序">
                                <InputNumber min={0} style={{ width: "100%" }} />
                            </Form.Item>
                        </Col>
                        <Col span={12}>
                            <Form.Item name="enabled" label="启用" valuePropName="checked">
                                <Switch checkedChildren="启用" unCheckedChildren="停用" />
                            </Form.Item>
                        </Col>
                    </Row>
                </Form>
            </Modal>
        </section>
    );
}
