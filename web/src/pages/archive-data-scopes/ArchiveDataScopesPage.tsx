import { PlusOutlined } from "@ant-design/icons";
import { useMutation, useQueries, useQuery, useQueryClient } from "@tanstack/react-query";
import {
    Button,
    Card,
    Checkbox,
    Drawer,
    Form,
    Input,
    Select,
    Space,
    Switch,
    Table,
    Tag,
    Typography,
    message,
} from "antd";
import type { TableColumnsType } from "antd";
import { useState } from "react";

import {
    createArchiveDataScope,
    getCurrentUserPermissions,
    listArchiveCategories,
    listArchiveDataScopeFields,
    listArchiveDataScopes,
    listArchiveFonds,
    listArchiveRetentionPeriods,
    listArchiveSecurityLevels,
    updateArchiveDataScope,
} from "@/shared/api/archive";
import type {
    ArchiveDataScopeRequest,
    ArchiveDataScopeDto,
    ArchiveFieldDto,
} from "@/shared/types/archive";

type ScopeFormValues = ArchiveDataScopeRequest & {
    fondsCodes?: string[];
    categoryIds?: number[];
    securityLevelIds?: number[];
    retentionPeriodIds?: number[];
    includeCategoryDescendants?: boolean;
};

const dataScopeQueryKey = ["archive-data-scopes"] as const;

export function ArchiveDataScopesPage() {
    const [form] = Form.useForm<ScopeFormValues>();
    const queryClient = useQueryClient();
    const [editing, setEditing] = useState<ArchiveDataScopeDto>();
    const [open, setOpen] = useState(false);
    const scopeType = Form.useWatch("scopeType", form);
    const dynamicFields =
        (Form.useWatch(["dynamicCondition", "dynamicFields"], form) as
            | Array<{ categoryId?: number }>
            | undefined) ?? [];
    const dynamicCategoryIds = uniqueNumbers(dynamicFields.map((item) => item?.categoryId));
    const scopesQuery = useQuery({
        queryKey: dataScopeQueryKey,
        queryFn: () => listArchiveDataScopes(false),
    });
    const currentPermissionsQuery = useQuery({
        queryKey: ["me", "permissions"],
        queryFn: () => getCurrentUserPermissions(),
    });
    const canManageDataScopes = Boolean(
        currentPermissionsQuery.data?.permissionCodes.includes("archive:data-scope:manage"),
    );
    const fondsQuery = useQuery({
        queryKey: ["archive-fonds", "enabled"],
        queryFn: () => listArchiveFonds(true),
    });
    const categoriesQuery = useQuery({
        queryKey: ["archive-categories", "enabled"],
        queryFn: () => listArchiveCategories(true),
    });
    const securityLevelsQuery = useQuery({
        queryKey: ["archive-security-levels", "enabled"],
        queryFn: () => listArchiveSecurityLevels(true),
    });
    const retentionPeriodsQuery = useQuery({
        queryKey: ["archive-retention-periods", "enabled"],
        queryFn: () => listArchiveRetentionPeriods(true),
    });
    const dataScopeFieldQueries = useQueries({
        queries: dynamicCategoryIds.map((categoryId) => ({
            queryKey: ["archive-data-scope-fields", categoryId],
            queryFn: () => listArchiveDataScopeFields(categoryId),
        })),
    });
    const fieldsByCategory = new Map<number, ArchiveFieldDto[]>();
    dynamicCategoryIds.forEach((categoryId, index) => {
        fieldsByCategory.set(categoryId, dataScopeFieldQueries[index]?.data?.items ?? []);
    });
    const saveMutation = useMutation({
        mutationFn: (values: ScopeFormValues) => {
            const request = toRequest(values);
            return editing
                ? updateArchiveDataScope(editing.id, request)
                : createArchiveDataScope(request);
        },
        onSuccess: async () => {
            await queryClient.invalidateQueries({ queryKey: dataScopeQueryKey });
            setOpen(false);
            setEditing(undefined);
            form.resetFields();
            void message.success("数据范围已保存");
        },
        onError: (error) => {
            void message.error(error instanceof Error ? error.message : "保存失败");
        },
    });
    const columns: TableColumnsType<ArchiveDataScopeDto> = [
        { title: "范围编码", dataIndex: "scopeCode", key: "scopeCode", width: 180 },
        { title: "范围名称", dataIndex: "scopeName", key: "scopeName" },
        {
            title: "范围",
            dataIndex: "scopeType",
            key: "scopeType",
            width: 120,
            render: (value: ArchiveDataScopeDto["scopeType"]) =>
                value === "ALL" ? <Tag color="blue">*</Tag> : <Tag>条件</Tag>,
        },
        {
            title: "条件",
            key: "conditions",
            render: (_, row) => conditionText(row),
        },
        {
            title: "启用",
            dataIndex: "enabled",
            key: "enabled",
            width: 100,
            render: (enabled: boolean) =>
                enabled ? <Tag color="success">启用</Tag> : <Tag>停用</Tag>,
        },
        {
            title: "操作",
            key: "actions",
            width: 100,
            render: (_, row) => (
                <Button
                    disabled={!canManageDataScopes}
                    size="small"
                    type="link"
                    onClick={() => editScope(row)}
                >
                    编辑
                </Button>
            ),
        },
    ];

    function createScope() {
        setEditing(undefined);
        form.setFieldsValue({
            scopeType: "CONDITIONAL",
            enabled: true,
            dimensions: [],
            dynamicCondition: { dynamicFields: [] },
        });
        setOpen(true);
    }

    function editScope(row: ArchiveDataScopeDto) {
        setEditing(row);
        form.setFieldsValue(toFormValues(row));
        setOpen(true);
    }

    function closeDrawer() {
        setOpen(false);
        setEditing(undefined);
        form.resetFields();
    }

    return (
        <section className="am-page">
            <div className="am-page__header">
                <Typography.Title level={1}>数据范围</Typography.Title>
                <Space>
                    <Button
                        disabled={!canManageDataScopes}
                        icon={<PlusOutlined />}
                        type="primary"
                        onClick={createScope}
                    >
                        新建范围
                    </Button>
                </Space>
            </div>
            <Card>
                <Table<ArchiveDataScopeDto>
                    columns={columns}
                    dataSource={scopesQuery.data?.items ?? []}
                    loading={scopesQuery.isLoading}
                    pagination={false}
                    rowKey="id"
                />
            </Card>
            <Drawer
                destroyOnClose
                open={open}
                title={editing ? "编辑数据范围" : "新建数据范围"}
                width={640}
                onClose={closeDrawer}
                extra={
                    <Button
                        disabled={!canManageDataScopes}
                        loading={saveMutation.isPending}
                        type="primary"
                        onClick={() => form.submit()}
                    >
                        保存
                    </Button>
                }
            >
                <Form<ScopeFormValues>
                    form={form}
                    layout="vertical"
                    onFinish={(values) => saveMutation.mutate(values)}
                >
                    <Form.Item
                        label="范围编码"
                        name="scopeCode"
                        rules={[{ required: true, message: "请输入范围编码" }]}
                    >
                        <Input />
                    </Form.Item>
                    <Form.Item
                        label="范围名称"
                        name="scopeName"
                        rules={[{ required: true, message: "请输入范围名称" }]}
                    >
                        <Input />
                    </Form.Item>
                    <Form.Item label="范围类型" name="scopeType" rules={[{ required: true }]}>
                        <Select
                            options={[
                                { label: "* 任意范围", value: "ALL" },
                                { label: "条件范围", value: "CONDITIONAL" },
                            ]}
                        />
                    </Form.Item>
                    {scopeType !== "ALL" ? (
                        <>
                            <Form.Item label="全宗范围" name="fondsCodes">
                                <Select
                                    allowClear
                                    mode="multiple"
                                    options={(fondsQuery.data?.items ?? []).map((item) => ({
                                        label: `${item.fondsCode} ${item.fondsName}`,
                                        value: item.fondsCode,
                                    }))}
                                />
                            </Form.Item>
                            <Form.Item label="分类范围" name="categoryIds">
                                <Select
                                    allowClear
                                    mode="multiple"
                                    options={(categoriesQuery.data?.items ?? []).map((item) => ({
                                        label: `${item.categoryCode} ${item.categoryName}`,
                                        value: item.id,
                                    }))}
                                />
                            </Form.Item>
                            <Form.Item name="includeCategoryDescendants" valuePropName="checked">
                                <Checkbox>包含所选分类子级</Checkbox>
                            </Form.Item>
                            <Form.Item label="密级范围" name="securityLevelIds">
                                <Select
                                    allowClear
                                    mode="multiple"
                                    options={(securityLevelsQuery.data?.items ?? []).map(
                                        (item) => ({
                                            label: item.levelName,
                                            value: item.id,
                                        }),
                                    )}
                                />
                            </Form.Item>
                            <Form.Item label="保管期限范围" name="retentionPeriodIds">
                                <Select
                                    allowClear
                                    mode="multiple"
                                    options={(retentionPeriodsQuery.data?.items ?? []).map(
                                        (item) => ({
                                            label: item.periodName,
                                            value: item.id,
                                        }),
                                    )}
                                />
                            </Form.Item>
                            <Form.List name={["dynamicCondition", "dynamicFields"]}>
                                {(fields, operations) => (
                                    <Space direction="vertical" style={{ width: "100%" }}>
                                        {fields.map((field) => (
                                            <Space key={field.key} align="start">
                                                <Form.Item
                                                    {...field}
                                                    label="动态分类"
                                                    name={[field.name, "categoryId"]}
                                                    rules={[
                                                        { required: true, message: "请选择分类" },
                                                    ]}
                                                >
                                                    <Select
                                                        style={{ width: 160 }}
                                                        options={(
                                                            categoriesQuery.data?.items ?? []
                                                        ).map((item) => ({
                                                            label: item.categoryName,
                                                            value: item.id,
                                                        }))}
                                                    />
                                                </Form.Item>
                                                <Form.Item
                                                    {...field}
                                                    label="字段"
                                                    name={[field.name, "fieldCode"]}
                                                    rules={[
                                                        { required: true, message: "请选择字段" },
                                                    ]}
                                                >
                                                    <Select
                                                        style={{ width: 140 }}
                                                        options={fieldOptions(
                                                            fieldsByCategory.get(
                                                                dynamicFields[field.name]
                                                                    ?.categoryId ?? -1,
                                                            ),
                                                        )}
                                                    />
                                                </Form.Item>
                                                <Form.Item
                                                    {...field}
                                                    label="操作符"
                                                    name={[field.name, "operator"]}
                                                    rules={[
                                                        { required: true, message: "请选择操作符" },
                                                    ]}
                                                >
                                                    <Select
                                                        style={{ width: 120 }}
                                                        options={[
                                                            { label: "等于", value: "EQ" },
                                                            { label: "包含任一", value: "IN" },
                                                            { label: "为空", value: "IS_NULL" },
                                                            { label: "非空", value: "IS_NOT_NULL" },
                                                        ]}
                                                    />
                                                </Form.Item>
                                                <Form.Item
                                                    {...field}
                                                    label="值"
                                                    name={[field.name, "values"]}
                                                >
                                                    <Select mode="tags" style={{ width: 180 }} />
                                                </Form.Item>
                                                <Button
                                                    onClick={() => operations.remove(field.name)}
                                                >
                                                    删除
                                                </Button>
                                            </Space>
                                        ))}
                                        <Button
                                            onClick={() =>
                                                operations.add({ operator: "EQ", values: [] })
                                            }
                                        >
                                            添加动态字段条件
                                        </Button>
                                    </Space>
                                )}
                            </Form.List>
                        </>
                    ) : null}
                    <Form.Item label="说明" name="description">
                        <Input.TextArea rows={3} />
                    </Form.Item>
                    <Form.Item name="enabled" valuePropName="checked">
                        <Switch checkedChildren="启用" unCheckedChildren="停用" />
                    </Form.Item>
                </Form>
            </Drawer>
        </section>
    );
}

function uniqueNumbers(values: unknown[]) {
    return [...new Set(values.filter((value): value is number => typeof value === "number"))];
}

function fieldOptions(fields?: ArchiveFieldDto[]) {
    return (fields ?? [])
        .filter((field) => field.fieldSource !== "BUILTIN")
        .map((field) => ({
            label: field.fieldName,
            value: field.fieldCode,
        }));
}

function toRequest(values: ScopeFormValues): ArchiveDataScopeRequest {
    if (values.scopeType === "ALL") {
        return {
            scopeCode: values.scopeCode,
            scopeName: values.scopeName,
            scopeType: values.scopeType,
            dimensions: [],
            dynamicCondition: undefined,
            enabled: values.enabled,
            description: values.description,
        };
    }
    return {
        scopeCode: values.scopeCode,
        scopeName: values.scopeName,
        scopeType: values.scopeType,
        dimensions: [
            ...(values.fondsCodes ?? []).map((targetCode) => ({
                dimensionType: "FONDS" as const,
                targetCode,
                includeDescendants: false,
            })),
            ...(values.categoryIds ?? []).map((targetId) => ({
                dimensionType: "CATEGORY" as const,
                targetId,
                includeDescendants: Boolean(values.includeCategoryDescendants),
            })),
            ...(values.securityLevelIds ?? []).map((targetId) => ({
                dimensionType: "SECURITY_LEVEL" as const,
                targetId,
                includeDescendants: false,
            })),
            ...(values.retentionPeriodIds ?? []).map((targetId) => ({
                dimensionType: "RETENTION_PERIOD" as const,
                targetId,
                includeDescendants: false,
            })),
        ],
        dynamicCondition:
            (values.dynamicCondition?.dynamicFields?.length ?? 0) > 0
                ? values.dynamicCondition
                : undefined,
        enabled: values.enabled,
        description: values.description,
    };
}

function toFormValues(row: ArchiveDataScopeDto): ScopeFormValues {
    return {
        scopeCode: row.scopeCode,
        scopeName: row.scopeName,
        scopeType: row.scopeType,
        enabled: row.enabled,
        description: row.description,
        dimensions: row.dimensions,
        fondsCodes: row.dimensions
            .filter((item) => item.dimensionType === "FONDS")
            .map((item) => item.targetCode)
            .filter((item): item is string => Boolean(item)),
        categoryIds: row.dimensions
            .filter((item) => item.dimensionType === "CATEGORY")
            .map((item) => item.targetId)
            .filter((item): item is number => typeof item === "number"),
        securityLevelIds: row.dimensions
            .filter((item) => item.dimensionType === "SECURITY_LEVEL")
            .map((item) => item.targetId)
            .filter((item): item is number => typeof item === "number"),
        retentionPeriodIds: row.dimensions
            .filter((item) => item.dimensionType === "RETENTION_PERIOD")
            .map((item) => item.targetId)
            .filter((item): item is number => typeof item === "number"),
        includeCategoryDescendants: row.dimensions.some(
            (item) => item.dimensionType === "CATEGORY" && item.includeDescendants,
        ),
        dynamicCondition: row.dynamicCondition ?? { dynamicFields: [] },
    };
}

function conditionText(row: ArchiveDataScopeDto) {
    if (row.scopeType === "ALL") {
        return "*";
    }
    const dimensions = row.dimensions.map((item) => item.dimensionType).join("、");
    const dynamicCount = row.dynamicCondition?.dynamicFields.length ?? 0;
    return [dimensions, dynamicCount > 0 ? `动态字段 ${dynamicCount} 条` : undefined]
        .filter(Boolean)
        .join("；");
}
