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
import { useEffect, useMemo, useState } from "react";

import {
    buildArchiveCategoryTable,
    createArchiveCategory,
    createArchiveField,
    deleteArchiveCategory,
    deleteArchiveField,
    listArchiveCategories,
    listArchiveFields,
    updateArchiveCategory,
    updateArchiveField,
} from "@/shared/api/archive";
import type {
    ArchiveCategoryDto,
    ArchiveFieldDto,
    ArchiveFieldType,
    ArchiveLevel,
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

    // ── 字段 CRUD ──
    const [fieldModalOpen, setFieldModalOpen] = useState(false);
    const [fieldModalMode, setFieldModalMode] = useState<"create" | "edit">("create");
    const [editingField, setEditingField] = useState<ArchiveFieldDto | null>(null);
    const [fieldForm] = Form.useForm();

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

    const createFieldMutation = useMutation({
        mutationFn: ({
            categoryId,
            payload,
        }: {
            categoryId: number;
            payload: Parameters<typeof createArchiveField>[1];
        }) => createArchiveField(categoryId, payload),
        onSuccess: () => {
            message.success("字段创建成功");
            queryClient.invalidateQueries({ queryKey: ["archive-fields", selectedCategoryId] });
            handleFieldModalClose();
        },
        onError: (e: Error) => message.error(e.message),
    });

    const updateFieldMutation = useMutation({
        mutationFn: ({
            categoryId,
            fieldId,
            payload,
        }: {
            categoryId: number;
            fieldId: number;
            payload: Parameters<typeof updateArchiveField>[2];
        }) => updateArchiveField(categoryId, fieldId, payload),
        onSuccess: () => {
            message.success("字段更新成功");
            queryClient.invalidateQueries({ queryKey: ["archive-fields", selectedCategoryId] });
            handleFieldModalClose();
        },
        onError: (e: Error) => message.error(e.message),
    });

    const deleteFieldMutation = useMutation({
        mutationFn: ({ categoryId, fieldId }: { categoryId: number; fieldId: number }) =>
            deleteArchiveField(categoryId, fieldId),
        onSuccess: () => {
            message.success("字段已删除");
            queryClient.invalidateQueries({ queryKey: ["archive-fields", selectedCategoryId] });
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

    // ── 字段 Modal ──

    function handleFieldModalClose() {
        setFieldModalOpen(false);
        setEditingField(null);
        fieldForm.resetFields();
    }

    function openCreateFieldModal() {
        setFieldModalMode("create");
        setEditingField(null);
        fieldForm.resetFields();
        fieldForm.setFieldsValue({
            archiveLevel: "ITEM",
            enabled: true,
            fieldType: "TEXT",
            listVisible: true,
            detailVisible: true,
            editVisible: true,
            exactSearchable: false,
            dataScopeFilterable: false,
            sortOrder: 0,
            listSortOrder: 0,
            detailSortOrder: 0,
            editSortOrder: 0,
        });
        setFieldModalOpen(true);
    }

    function openEditFieldModal(field: ArchiveFieldDto) {
        setFieldModalMode("edit");
        setEditingField(field);
        fieldForm.setFieldsValue({
            archiveLevel: field.archiveLevel,
            fieldCode: field.fieldCode,
            fieldName: field.fieldName,
            fieldType: field.fieldType,
            textLength: field.textLength,
            decimalPrecision: field.decimalPrecision,
            decimalScale: field.decimalScale,
            enabled: field.enabled,
            listVisible: field.listVisible,
            listSortOrder: field.listSortOrder,
            detailVisible: field.detailVisible,
            detailSortOrder: field.detailSortOrder,
            editVisible: field.editVisible,
            editSortOrder: field.editSortOrder,
            exactSearchable: field.exactSearchable,
            dataScopeFilterable: field.dataScopeFilterable,
            sortOrder: field.sortOrder,
        });
        setFieldModalOpen(true);
    }

    async function handleFieldSubmit() {
        const values = await fieldForm.validateFields();
        const payload = {
            archiveLevel: values.archiveLevel as ArchiveLevel,
            fieldCode: values.fieldCode,
            fieldName: values.fieldName,
            fieldType: values.fieldType as ArchiveFieldType,
            textLength: values.textLength ?? undefined,
            decimalPrecision: values.decimalPrecision ?? undefined,
            decimalScale: values.decimalScale ?? undefined,
            editControl: undefined,
            listVisible: values.listVisible ?? true,
            listWidth: undefined,
            listSortOrder: values.listSortOrder ?? 0,
            detailVisible: values.detailVisible ?? true,
            detailColSpan: 1,
            detailSortOrder: values.detailSortOrder ?? 0,
            editVisible: values.editVisible ?? true,
            editColSpan: 1,
            editSortOrder: values.editSortOrder ?? 0,
            exactSearchable: values.exactSearchable ?? false,
            dataScopeFilterable: values.dataScopeFilterable ?? false,
            enabled: values.enabled ?? true,
            sortOrder: values.sortOrder ?? 0,
        };
        const categoryId = selectedCategoryId!;
        if (fieldModalMode === "create") {
            createFieldMutation.mutate({ categoryId, payload });
        } else if (editingField) {
            updateFieldMutation.mutate({
                categoryId,
                fieldId: editingField.id,
                payload,
            });
        }
    }

    const fieldColumns: TableColumnsType<ArchiveFieldDto> = useMemo(
        () => [
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
                render: (v: boolean) => (
                    <Tag color={v ? "green" : "default"}>{v ? "启用" : "停用"}</Tag>
                ),
            },
            {
                title: "操作",
                key: "actions",
                width: 120,
                render: (_, record) => (
                    <Space size="small">
                        <Button size="small" type="link" onClick={() => openEditFieldModal(record)}>
                            编辑
                        </Button>
                        <Popconfirm
                            title="确定删除此字段？"
                            onConfirm={() =>
                                deleteFieldMutation.mutate({
                                    categoryId: record.categoryId,
                                    fieldId: record.id,
                                })
                            }
                            okText="删除"
                            cancelText="取消"
                        >
                            <Button
                                size="small"
                                type="link"
                                danger
                                loading={deleteFieldMutation.isPending}
                            >
                                删除
                            </Button>
                        </Popconfirm>
                    </Space>
                ),
            },
        ],
        [deleteFieldMutation.isPending, deleteFieldMutation.mutate, openEditFieldModal],
    );

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
                                    <Button size="small" onClick={openCreateFieldModal}>
                                        新建字段
                                    </Button>
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
                <Form form={form} layout="vertical">
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

            {/* 新建 / 编辑字段 */}
            <Modal
                title={fieldModalMode === "create" ? "新建字段" : "编辑字段"}
                open={fieldModalOpen}
                onOk={handleFieldSubmit}
                onCancel={handleFieldModalClose}
                confirmLoading={createFieldMutation.isPending || updateFieldMutation.isPending}
                destroyOnClose
                width={640}
            >
                <Form form={fieldForm} layout="vertical">
                    <Row gutter={16}>
                        <Col span={12}>
                            <Form.Item
                                name="archiveLevel"
                                label="层级"
                                rules={[{ required: true, message: "请选择层级" }]}
                            >
                                <Select
                                    options={[
                                        { label: "条目", value: "ITEM" },
                                        { label: "案卷", value: "VOLUME" },
                                    ]}
                                />
                            </Form.Item>
                        </Col>
                        <Col span={12}>
                            <Form.Item
                                name="fieldType"
                                label="字段类型"
                                rules={[{ required: true, message: "请选择字段类型" }]}
                            >
                                <Select
                                    options={Object.entries(fieldTypeLabels).map(
                                        ([value, label]) => ({
                                            value,
                                            label,
                                        }),
                                    )}
                                />
                            </Form.Item>
                        </Col>
                    </Row>
                    <Row gutter={16}>
                        <Col span={12}>
                            <Form.Item
                                name="fieldCode"
                                label="字段编码"
                                rules={[{ required: true, message: "请输入字段编码" }]}
                            >
                                <Input placeholder="例如：project_name" />
                            </Form.Item>
                        </Col>
                        <Col span={12}>
                            <Form.Item
                                name="fieldName"
                                label="字段名称"
                                rules={[{ required: true, message: "请输入字段名称" }]}
                            >
                                <Input placeholder="例如：项目名称" />
                            </Form.Item>
                        </Col>
                    </Row>
                    <Form.Item
                        noStyle
                        shouldUpdate={(prev, next) => prev.fieldType !== next.fieldType}
                    >
                        {({ getFieldValue }) => {
                            const ft = getFieldValue("fieldType") as string;
                            if (ft === "TEXT") {
                                return (
                                    <Form.Item name="textLength" label="文本长度">
                                        <InputNumber min={1} style={{ width: "100%" }} />
                                    </Form.Item>
                                );
                            }
                            if (ft === "DECIMAL") {
                                return (
                                    <Row gutter={16}>
                                        <Col span={12}>
                                            <Form.Item name="decimalPrecision" label="总位数">
                                                <InputNumber min={1} style={{ width: "100%" }} />
                                            </Form.Item>
                                        </Col>
                                        <Col span={12}>
                                            <Form.Item name="decimalScale" label="小数位">
                                                <InputNumber min={0} style={{ width: "100%" }} />
                                            </Form.Item>
                                        </Col>
                                    </Row>
                                );
                            }
                            return null;
                        }}
                    </Form.Item>
                    <Typography.Title level={5} style={{ marginTop: 8 }}>
                        显示与搜索
                    </Typography.Title>
                    <Row gutter={16}>
                        <Col span={8}>
                            <Form.Item name="listVisible" label="列表显示" valuePropName="checked">
                                <Switch />
                            </Form.Item>
                        </Col>
                        <Col span={8}>
                            <Form.Item
                                name="detailVisible"
                                label="详情显示"
                                valuePropName="checked"
                            >
                                <Switch />
                            </Form.Item>
                        </Col>
                        <Col span={8}>
                            <Form.Item name="editVisible" label="编辑可见" valuePropName="checked">
                                <Switch />
                            </Form.Item>
                        </Col>
                    </Row>
                    <Row gutter={16}>
                        <Col span={8}>
                            <Form.Item
                                name="exactSearchable"
                                label="精确检索"
                                valuePropName="checked"
                            >
                                <Switch />
                            </Form.Item>
                        </Col>
                        <Col span={8}>
                            <Form.Item
                                name="dataScopeFilterable"
                                label="可用于数据范围"
                                valuePropName="checked"
                            >
                                <Switch />
                            </Form.Item>
                        </Col>
                        <Col span={8}>
                            <Form.Item name="enabled" label="启用" valuePropName="checked">
                                <Switch />
                            </Form.Item>
                        </Col>
                    </Row>
                    <Typography.Title level={5}>排序</Typography.Title>
                    <Row gutter={16}>
                        <Col span={6}>
                            <Form.Item name="sortOrder" label="全局排序">
                                <InputNumber min={0} style={{ width: "100%" }} />
                            </Form.Item>
                        </Col>
                        <Col span={6}>
                            <Form.Item name="listSortOrder" label="列表排序">
                                <InputNumber min={0} style={{ width: "100%" }} />
                            </Form.Item>
                        </Col>
                        <Col span={6}>
                            <Form.Item name="detailSortOrder" label="详情排序">
                                <InputNumber min={0} style={{ width: "100%" }} />
                            </Form.Item>
                        </Col>
                        <Col span={6}>
                            <Form.Item name="editSortOrder" label="编辑排序">
                                <InputNumber min={0} style={{ width: "100%" }} />
                            </Form.Item>
                        </Col>
                    </Row>
                </Form>
            </Modal>
        </section>
    );
}
