import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
    Button,
    Card,
    Col,
    Descriptions,
    Divider,
    Empty,
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
    Typography,
} from "antd";
import type { TableColumnsType } from "antd";
import { useEffect, useState } from "react";

import {
    createArchiveGovernanceScheme,
    createArchiveGovernanceSchemeVersion,
    deleteArchiveGovernanceScheme,
    freezeArchiveGovernanceSchemeVersion,
    listArchiveGovernanceBindings,
    listArchiveGovernanceSchemeVersions,
    listArchiveGovernanceSchemes,
    listArchiveGovernanceScopes,
    publishArchiveGovernanceSchemeVersion,
    replaceArchiveGovernanceBindings,
    replaceArchiveGovernanceScopes,
    resolveDefaultArchiveGovernanceVersion,
    retireArchiveGovernanceSchemeVersion,
    updateArchiveGovernanceScheme,
} from "@/shared/api/archive";
import type {
    ArchiveGovernanceBindingDto,
    ArchiveGovernanceBindingRequest,
    ArchiveGovernanceBindingType,
    ArchiveGovernanceSchemeDto,
    ArchiveGovernanceSchemeVersionDto,
    ArchiveGovernanceSchemeVersionStatus,
    ArchiveGovernanceScopeDto,
    ArchiveGovernanceScopeRequest,
    ArchiveGovernanceScopeType,
} from "@/shared/types/archive";

const schemeQueryKey = ["archive-governance-schemes"] as const;

function versionQueryKey(schemeId: number | null) {
    return ["archive-governance-versions", schemeId] as const;
}

function scopeQueryKey(versionId: number | null) {
    return ["archive-governance-version-scopes", versionId] as const;
}

function bindingQueryKey(versionId: number | null) {
    return ["archive-governance-version-bindings", versionId] as const;
}

const statusLabels: Record<ArchiveGovernanceSchemeVersionStatus, string> = {
    DRAFT: "草稿",
    PUBLISHED: "已发布",
    FROZEN: "已冻结",
    RETIRED: "已退役",
};

const scopeTypeLabels: Record<ArchiveGovernanceScopeType, string> = {
    GLOBAL: "全局默认",
    FONDS: "全宗",
    CATEGORY: "分类",
};

const bindingTypeLabels: Record<ArchiveGovernanceBindingType, string> = {
    ONTOLOGY: "本体",
    RULE_SET: "规则集",
    CLASSIFICATION_SCHEME: "分类方案",
    DESCRIPTION_PROFILE: "著录方案",
    REFERENCE_CODE_RULE: "档号规则",
};

const scopeTypeOptions = Object.entries(scopeTypeLabels).map(([value, label]) => ({
    value,
    label,
}));

const bindingTypeOptions = Object.entries(bindingTypeLabels).map(([value, label]) => ({
    value,
    label,
}));

let draftCounter = 0;

interface ScopeDraft {
    draftKey: string;
    id?: number;
    scopeType: ArchiveGovernanceScopeType;
    fondsCode?: string;
    categoryCode?: string;
    defaultFlag: boolean;
}

interface BindingDraft {
    draftKey: string;
    id?: number;
    bindingType: ArchiveGovernanceBindingType;
    targetType?: string;
    targetId?: number;
    targetCode?: string;
    bindingOrder: number;
}

interface ResolveDefaultFormValues {
    fondsCode?: string;
    categoryCode?: string;
}

export function ArchiveGovernancePage() {
    const queryClient = useQueryClient();
    const [selectedSchemeId, setSelectedSchemeId] = useState<number | null>(null);
    const [selectedVersionId, setSelectedVersionId] = useState<number | null>(null);
    const [schemeModalOpen, setSchemeModalOpen] = useState(false);
    const [editingScheme, setEditingScheme] = useState<ArchiveGovernanceSchemeDto | null>(null);
    const [versionModalOpen, setVersionModalOpen] = useState(false);
    const [scopeDrafts, setScopeDrafts] = useState<ScopeDraft[]>([]);
    const [bindingDrafts, setBindingDrafts] = useState<BindingDraft[]>([]);
    const [resolvedVersion, setResolvedVersion] =
        useState<ArchiveGovernanceSchemeVersionDto | null>(null);
    const [schemeForm] = Form.useForm();
    const [versionForm] = Form.useForm();
    const [resolveForm] = Form.useForm<ResolveDefaultFormValues>();

    const schemesQuery = useQuery({
        queryKey: schemeQueryKey,
        queryFn: () => listArchiveGovernanceSchemes(),
    });

    const versionsQuery = useQuery({
        queryKey: versionQueryKey(selectedSchemeId),
        queryFn: () => listArchiveGovernanceSchemeVersions(selectedSchemeId!),
        enabled: selectedSchemeId != null,
    });

    const versions = versionsQuery.data?.items ?? [];
    const selectedVersion = versions.find((version) => version.id === selectedVersionId) ?? null;
    const selectedVersionReadonly = selectedVersion?.status !== "DRAFT";

    const scopesQuery = useQuery({
        queryKey: scopeQueryKey(selectedVersionId),
        queryFn: () => listArchiveGovernanceScopes(selectedVersionId!),
        enabled: selectedVersionId != null,
    });

    const bindingsQuery = useQuery({
        queryKey: bindingQueryKey(selectedVersionId),
        queryFn: () => listArchiveGovernanceBindings(selectedVersionId!),
        enabled: selectedVersionId != null,
    });

    const schemes = schemesQuery.data?.items ?? [];

    useEffect(() => {
        if (selectedSchemeId == null && schemes.length > 0) {
            setSelectedSchemeId(schemes[0].id);
        }
    }, [schemes, selectedSchemeId]);

    useEffect(() => {
        if (versionsQuery.data == null) {
            return;
        }
        if (versions.length === 0) {
            if (selectedVersionId != null) {
                setSelectedVersionId(null);
            }
            return;
        }
        if (
            selectedVersionId == null ||
            !versions.some((version) => version.id === selectedVersionId)
        ) {
            setSelectedVersionId(versions[0].id);
        }
    }, [selectedVersionId, versions, versionsQuery.data]);

    useEffect(() => {
        setScopeDrafts((scopesQuery.data?.items ?? []).map(toScopeDraft));
    }, [scopesQuery.data]);

    useEffect(() => {
        setBindingDrafts((bindingsQuery.data?.items ?? []).map(toBindingDraft));
    }, [bindingsQuery.data]);

    const createSchemeMutation = useMutation({
        mutationFn: createArchiveGovernanceScheme,
        onSuccess: (scheme) => {
            message.success("治理方案已创建");
            setSelectedSchemeId(scheme.id);
            setSelectedVersionId(null);
            setSchemeModalOpen(false);
            schemeForm.resetFields();
            queryClient.invalidateQueries({ queryKey: schemeQueryKey });
        },
        onError: (error: Error) => message.error(error.message),
    });

    const updateSchemeMutation = useMutation({
        mutationFn: ({
            id,
            payload,
        }: {
            id: number;
            payload: Parameters<typeof updateArchiveGovernanceScheme>[1];
        }) => updateArchiveGovernanceScheme(id, payload),
        onSuccess: () => {
            message.success("治理方案已更新");
            setSchemeModalOpen(false);
            setEditingScheme(null);
            schemeForm.resetFields();
            queryClient.invalidateQueries({ queryKey: schemeQueryKey });
        },
        onError: (error: Error) => message.error(error.message),
    });

    const deleteSchemeMutation = useMutation({
        mutationFn: deleteArchiveGovernanceScheme,
        onSuccess: () => {
            message.success("治理方案已删除");
            setSelectedSchemeId(null);
            setSelectedVersionId(null);
            queryClient.invalidateQueries({ queryKey: schemeQueryKey });
        },
        onError: (error: Error) => message.error(error.message),
    });

    const createVersionMutation = useMutation({
        mutationFn: ({
            schemeId,
            payload,
        }: {
            schemeId: number;
            payload: Parameters<typeof createArchiveGovernanceSchemeVersion>[1];
        }) => createArchiveGovernanceSchemeVersion(schemeId, payload),
        onSuccess: (version) => {
            message.success("版本已创建");
            setSelectedVersionId(version.id);
            setVersionModalOpen(false);
            versionForm.resetFields();
            queryClient.invalidateQueries({
                queryKey: versionQueryKey(selectedSchemeId),
            });
        },
        onError: (error: Error) => message.error(error.message),
    });

    const statusMutation = useMutation({
        mutationFn: ({ id, action }: { id: number; action: "publish" | "freeze" | "retire" }) => {
            if (action === "publish") {
                return publishArchiveGovernanceSchemeVersion(id);
            }
            if (action === "freeze") {
                return freezeArchiveGovernanceSchemeVersion(id);
            }
            return retireArchiveGovernanceSchemeVersion(id);
        },
        onSuccess: () => {
            message.success("版本状态已更新");
            queryClient.invalidateQueries({
                queryKey: versionQueryKey(selectedSchemeId),
            });
        },
        onError: (error: Error) => message.error(error.message),
    });

    const saveScopesMutation = useMutation({
        mutationFn: ({
            versionId,
            payload,
        }: {
            versionId: number;
            payload: ArchiveGovernanceScopeRequest[];
        }) => replaceArchiveGovernanceScopes(versionId, payload),
        onSuccess: (_, variables) => {
            message.success("适用范围已保存");
            queryClient.invalidateQueries({ queryKey: scopeQueryKey(variables.versionId) });
        },
        onError: (error: Error) => message.error(error.message),
    });

    const saveBindingsMutation = useMutation({
        mutationFn: ({
            versionId,
            payload,
        }: {
            versionId: number;
            payload: ArchiveGovernanceBindingRequest[];
        }) => replaceArchiveGovernanceBindings(versionId, payload),
        onSuccess: (_, variables) => {
            message.success("装配绑定已保存");
            queryClient.invalidateQueries({ queryKey: bindingQueryKey(variables.versionId) });
        },
        onError: (error: Error) => message.error(error.message),
    });

    const resolveDefaultMutation = useMutation({
        mutationFn: resolveDefaultArchiveGovernanceVersion,
        onSuccess: (version) => {
            setResolvedVersion(version);
        },
        onError: (error: Error) => {
            setResolvedVersion(null);
            message.error(error.message);
        },
    });

    function openCreateScheme() {
        setEditingScheme(null);
        schemeForm.setFieldsValue({ enabled: true, sortOrder: 0 });
        setSchemeModalOpen(true);
    }

    function openEditScheme(scheme: ArchiveGovernanceSchemeDto) {
        setEditingScheme(scheme);
        schemeForm.setFieldsValue(scheme);
        setSchemeModalOpen(true);
    }

    async function submitScheme() {
        const values = await schemeForm.validateFields();
        const payload = {
            schemeCode: values.schemeCode,
            schemeName: values.schemeName,
            description: values.description,
            enabled: values.enabled ?? true,
            sortOrder: values.sortOrder ?? 0,
        };
        if (editingScheme) {
            updateSchemeMutation.mutate({ id: editingScheme.id, payload });
        } else {
            createSchemeMutation.mutate(payload);
        }
    }

    async function submitVersion() {
        if (selectedSchemeId == null) {
            return;
        }
        const values = await versionForm.validateFields();
        createVersionMutation.mutate({
            schemeId: selectedSchemeId,
            payload: {
                versionCode: values.versionCode,
                versionDescription: values.versionDescription,
            },
        });
    }

    async function submitResolveDefault() {
        const values = await resolveForm.validateFields();
        resolveDefaultMutation.mutate({
            fondsCode: trimToUndefined(values.fondsCode),
            categoryCode: trimToUndefined(values.categoryCode),
        });
    }

    function saveScopes() {
        if (selectedVersionId == null) {
            return;
        }
        saveScopesMutation.mutate({
            versionId: selectedVersionId,
            payload: scopeDrafts.map(toScopeRequest),
        });
    }

    function saveBindings() {
        if (selectedVersionId == null) {
            return;
        }
        saveBindingsMutation.mutate({
            versionId: selectedVersionId,
            payload: bindingDrafts.map(toBindingRequest),
        });
    }

    function updateScopeDraft(draftKey: string, patch: Partial<ScopeDraft>) {
        setScopeDrafts((current) =>
            current.map((draft) => (draft.draftKey === draftKey ? { ...draft, ...patch } : draft)),
        );
    }

    function updateBindingDraft(draftKey: string, patch: Partial<BindingDraft>) {
        setBindingDrafts((current) =>
            current.map((draft) => (draft.draftKey === draftKey ? { ...draft, ...patch } : draft)),
        );
    }

    function removeScopeDraft(draftKey: string) {
        setScopeDrafts((current) => current.filter((draft) => draft.draftKey !== draftKey));
    }

    function removeBindingDraft(draftKey: string) {
        setBindingDrafts((current) => current.filter((draft) => draft.draftKey !== draftKey));
    }

    const schemeColumns: TableColumnsType<ArchiveGovernanceSchemeDto> = [
        { title: "编码", dataIndex: "schemeCode", width: 180 },
        { title: "名称", dataIndex: "schemeName" },
        {
            title: "启用",
            dataIndex: "enabled",
            width: 90,
            render: (enabled: boolean) => (
                <Tag color={enabled ? "blue" : "default"}>{enabled ? "启用" : "停用"}</Tag>
            ),
        },
        { title: "排序", dataIndex: "sortOrder", width: 80 },
        {
            title: "操作",
            width: 150,
            render: (_, row) => (
                <Space size={4}>
                    <Button size="small" type="link" onClick={() => openEditScheme(row)}>
                        编辑
                    </Button>
                    <Popconfirm
                        title="确认删除治理方案？"
                        onConfirm={() => deleteSchemeMutation.mutate(row.id)}
                    >
                        <Button danger size="small" type="link">
                            删除
                        </Button>
                    </Popconfirm>
                </Space>
            ),
        },
    ];

    const versionColumns: TableColumnsType<ArchiveGovernanceSchemeVersionDto> = [
        { title: "版本号", dataIndex: "versionCode", width: 150 },
        { title: "说明", dataIndex: "versionDescription" },
        {
            title: "状态",
            dataIndex: "status",
            width: 110,
            render: (status: ArchiveGovernanceSchemeVersionStatus) => (
                <Tag color={status === "PUBLISHED" ? "blue" : undefined}>
                    {statusLabels[status]}
                </Tag>
            ),
        },
        {
            title: "操作",
            width: 220,
            render: (_, row) => (
                <Space size={4}>
                    <Button
                        disabled={row.status !== "DRAFT"}
                        size="small"
                        onClick={() => statusMutation.mutate({ id: row.id, action: "publish" })}
                    >
                        发布
                    </Button>
                    <Button
                        disabled={row.status !== "PUBLISHED"}
                        size="small"
                        onClick={() => statusMutation.mutate({ id: row.id, action: "freeze" })}
                    >
                        冻结
                    </Button>
                    <Button
                        disabled={row.status !== "FROZEN"}
                        size="small"
                        onClick={() => statusMutation.mutate({ id: row.id, action: "retire" })}
                    >
                        退役
                    </Button>
                </Space>
            ),
        },
    ];

    const scopeColumns: TableColumnsType<ScopeDraft> = [
        {
            title: "范围类型",
            dataIndex: "scopeType",
            width: 150,
            render: (_, row) => (
                <Select
                    disabled={selectedVersionReadonly}
                    options={scopeTypeOptions}
                    value={row.scopeType}
                    onChange={(scopeType: ArchiveGovernanceScopeType) =>
                        updateScopeDraft(row.draftKey, normalizeScopeTypeChange(row, scopeType))
                    }
                />
            ),
        },
        {
            title: "全宗编码",
            dataIndex: "fondsCode",
            render: (_, row) => (
                <Input
                    disabled={selectedVersionReadonly || row.scopeType !== "FONDS"}
                    value={row.fondsCode}
                    onChange={(event) =>
                        updateScopeDraft(row.draftKey, { fondsCode: event.target.value })
                    }
                />
            ),
        },
        {
            title: "分类编码",
            dataIndex: "categoryCode",
            render: (_, row) => (
                <Input
                    disabled={selectedVersionReadonly || row.scopeType !== "CATEGORY"}
                    value={row.categoryCode}
                    onChange={(event) =>
                        updateScopeDraft(row.draftKey, { categoryCode: event.target.value })
                    }
                />
            ),
        },
        {
            title: "默认",
            dataIndex: "defaultFlag",
            width: 90,
            render: (_, row) => (
                <Switch
                    checked={row.defaultFlag}
                    disabled={selectedVersionReadonly}
                    onChange={(defaultFlag) => updateScopeDraft(row.draftKey, { defaultFlag })}
                />
            ),
        },
        {
            title: "操作",
            width: 90,
            render: (_, row) => (
                <Button
                    danger
                    disabled={selectedVersionReadonly}
                    size="small"
                    type="link"
                    onClick={() => removeScopeDraft(row.draftKey)}
                >
                    删除
                </Button>
            ),
        },
    ];

    const bindingColumns: TableColumnsType<BindingDraft> = [
        {
            title: "绑定类型",
            dataIndex: "bindingType",
            width: 160,
            render: (_, row) => (
                <Select
                    disabled={selectedVersionReadonly}
                    options={bindingTypeOptions}
                    value={row.bindingType}
                    onChange={(bindingType: ArchiveGovernanceBindingType) =>
                        updateBindingDraft(row.draftKey, { bindingType })
                    }
                />
            ),
        },
        {
            title: "目标类型",
            dataIndex: "targetType",
            width: 150,
            render: (_, row) => (
                <Input
                    disabled={selectedVersionReadonly}
                    value={row.targetType}
                    onChange={(event) =>
                        updateBindingDraft(row.draftKey, { targetType: event.target.value })
                    }
                />
            ),
        },
        {
            title: "目标 ID",
            dataIndex: "targetId",
            width: 130,
            render: (_, row) => (
                <InputNumber
                    disabled={selectedVersionReadonly}
                    min={0}
                    style={{ width: "100%" }}
                    value={row.targetId}
                    onChange={(value) =>
                        updateBindingDraft(row.draftKey, { targetId: toOptionalNumber(value) })
                    }
                />
            ),
        },
        {
            title: "目标编码",
            dataIndex: "targetCode",
            render: (_, row) => (
                <Input
                    disabled={selectedVersionReadonly}
                    value={row.targetCode}
                    onChange={(event) =>
                        updateBindingDraft(row.draftKey, { targetCode: event.target.value })
                    }
                />
            ),
        },
        {
            title: "排序",
            dataIndex: "bindingOrder",
            width: 110,
            render: (_, row) => (
                <InputNumber
                    disabled={selectedVersionReadonly}
                    style={{ width: "100%" }}
                    value={row.bindingOrder}
                    onChange={(value) =>
                        updateBindingDraft(row.draftKey, {
                            bindingOrder: toOptionalNumber(value) ?? 0,
                        })
                    }
                />
            ),
        },
        {
            title: "操作",
            width: 90,
            render: (_, row) => (
                <Button
                    danger
                    disabled={selectedVersionReadonly}
                    size="small"
                    type="link"
                    onClick={() => removeBindingDraft(row.draftKey)}
                >
                    删除
                </Button>
            ),
        },
    ];

    return (
        <section className="am-page">
            <div className="am-page__header">
                <Typography.Title level={1}>治理方案</Typography.Title>
                <Space>
                    <Button type="primary" onClick={openCreateScheme}>
                        新建方案
                    </Button>
                    <Button
                        disabled={selectedSchemeId == null}
                        onClick={() => {
                            versionForm.setFieldsValue({ versionCode: "v1" });
                            setVersionModalOpen(true);
                        }}
                    >
                        新建版本
                    </Button>
                </Space>
            </div>
            <Row gutter={[16, 16]}>
                <Col span={10}>
                    <Card>
                        <Table
                            columns={schemeColumns}
                            dataSource={schemes}
                            loading={schemesQuery.isLoading}
                            pagination={false}
                            rowKey="id"
                            rowSelection={{
                                selectedRowKeys: selectedSchemeId == null ? [] : [selectedSchemeId],
                                type: "radio",
                                onChange: ([id]) => {
                                    setSelectedSchemeId(Number(id));
                                    setSelectedVersionId(null);
                                    setResolvedVersion(null);
                                },
                            }}
                            size="middle"
                        />
                    </Card>
                </Col>
                <Col span={14}>
                    <Card>
                        <Table
                            columns={versionColumns}
                            dataSource={versions}
                            loading={versionsQuery.isLoading}
                            pagination={false}
                            rowKey="id"
                            rowSelection={{
                                selectedRowKeys:
                                    selectedVersionId == null ? [] : [selectedVersionId],
                                type: "radio",
                                onChange: ([id]) => {
                                    setSelectedVersionId(Number(id));
                                    setResolvedVersion(null);
                                },
                            }}
                            size="middle"
                        />
                    </Card>
                </Col>
                <Col span={24}>{renderWorkbench()}</Col>
            </Row>
            <Modal
                destroyOnHidden
                confirmLoading={createSchemeMutation.isPending || updateSchemeMutation.isPending}
                open={schemeModalOpen}
                title={editingScheme ? "编辑治理方案" : "新建治理方案"}
                onCancel={() => setSchemeModalOpen(false)}
                onOk={() => void submitScheme()}
            >
                <Form form={schemeForm} layout="vertical">
                    <Form.Item label="编码" name="schemeCode" rules={[{ required: true }]}>
                        <Input placeholder="default_governance" />
                    </Form.Item>
                    <Form.Item label="名称" name="schemeName" rules={[{ required: true }]}>
                        <Input />
                    </Form.Item>
                    <Form.Item label="说明" name="description">
                        <Input.TextArea autoSize={{ minRows: 2, maxRows: 4 }} />
                    </Form.Item>
                    <Form.Item label="排序" name="sortOrder">
                        <InputNumber min={0} style={{ width: "100%" }} />
                    </Form.Item>
                    <Form.Item label="启用" name="enabled" valuePropName="checked">
                        <Switch />
                    </Form.Item>
                </Form>
            </Modal>
            <Modal
                destroyOnHidden
                confirmLoading={createVersionMutation.isPending}
                open={versionModalOpen}
                title="新建治理方案版本"
                onCancel={() => setVersionModalOpen(false)}
                onOk={() => void submitVersion()}
            >
                <Form form={versionForm} layout="vertical">
                    <Form.Item label="版本号" name="versionCode" rules={[{ required: true }]}>
                        <Input placeholder="v1" />
                    </Form.Item>
                    <Form.Item label="版本说明" name="versionDescription">
                        <Input.TextArea autoSize={{ minRows: 2, maxRows: 4 }} />
                    </Form.Item>
                </Form>
            </Modal>
        </section>
    );

    function renderWorkbench() {
        if (selectedVersion == null) {
            return (
                <Card title="版本工作台">
                    <Empty description="请选择治理方案版本" />
                </Card>
            );
        }
        return (
            <Card title="版本工作台">
                <Descriptions bordered column={3} size="small">
                    <Descriptions.Item label="版本号">
                        {selectedVersion.versionCode}
                    </Descriptions.Item>
                    <Descriptions.Item label="状态">
                        <Tag color={selectedVersion.status === "PUBLISHED" ? "blue" : undefined}>
                            {statusLabels[selectedVersion.status]}
                        </Tag>
                    </Descriptions.Item>
                    <Descriptions.Item label="版本说明">
                        {selectedVersion.versionDescription ?? "-"}
                    </Descriptions.Item>
                    <Descriptions.Item label="发布时间">
                        {selectedVersion.publishedAt ?? "-"}
                    </Descriptions.Item>
                    <Descriptions.Item label="冻结时间">
                        {selectedVersion.frozenAt ?? "-"}
                    </Descriptions.Item>
                    <Descriptions.Item label="退役时间">
                        {selectedVersion.retiredAt ?? "-"}
                    </Descriptions.Item>
                </Descriptions>

                <Divider titlePlacement="left">适用范围</Divider>
                <Space style={{ marginBottom: 12 }}>
                    <Button
                        disabled={selectedVersionReadonly}
                        onClick={() =>
                            setScopeDrafts((current) => [...current, createScopeDraft()])
                        }
                    >
                        新增范围
                    </Button>
                    <Button
                        disabled={selectedVersionReadonly}
                        loading={saveScopesMutation.isPending}
                        type="primary"
                        onClick={saveScopes}
                    >
                        保存范围
                    </Button>
                </Space>
                <Table
                    columns={scopeColumns}
                    dataSource={scopeDrafts}
                    loading={scopesQuery.isLoading}
                    pagination={false}
                    rowKey="draftKey"
                    size="small"
                />

                <Divider titlePlacement="left">装配绑定</Divider>
                <Space style={{ marginBottom: 12 }}>
                    <Button
                        disabled={selectedVersionReadonly}
                        onClick={() =>
                            setBindingDrafts((current) => [...current, createBindingDraft()])
                        }
                    >
                        新增绑定
                    </Button>
                    <Button
                        disabled={selectedVersionReadonly}
                        loading={saveBindingsMutation.isPending}
                        type="primary"
                        onClick={saveBindings}
                    >
                        保存绑定
                    </Button>
                </Space>
                <Table
                    columns={bindingColumns}
                    dataSource={bindingDrafts}
                    loading={bindingsQuery.isLoading}
                    pagination={false}
                    rowKey="draftKey"
                    size="small"
                />

                <Divider titlePlacement="left">默认解析试算</Divider>
                <Form form={resolveForm} layout="inline">
                    <Form.Item label="全宗编码" name="fondsCode">
                        <Input placeholder="F001" />
                    </Form.Item>
                    <Form.Item label="分类编码" name="categoryCode">
                        <Input placeholder="case_file" />
                    </Form.Item>
                    <Form.Item>
                        <Button
                            loading={resolveDefaultMutation.isPending}
                            type="primary"
                            onClick={() => void submitResolveDefault()}
                        >
                            解析默认版本
                        </Button>
                    </Form.Item>
                </Form>
                {resolvedVersion ? (
                    <Descriptions bordered column={3} size="small" style={{ marginTop: 12 }}>
                        <Descriptions.Item label="命中版本">
                            {resolvedVersion.versionCode}
                        </Descriptions.Item>
                        <Descriptions.Item label="状态">
                            {statusLabels[resolvedVersion.status]}
                        </Descriptions.Item>
                        <Descriptions.Item label="说明">
                            {resolvedVersion.versionDescription ?? "-"}
                        </Descriptions.Item>
                    </Descriptions>
                ) : null}
            </Card>
        );
    }
}

function nextDraftKey() {
    draftCounter += 1;
    return `draft-${draftCounter}`;
}

function createScopeDraft(): ScopeDraft {
    return {
        draftKey: nextDraftKey(),
        scopeType: "GLOBAL",
        defaultFlag: true,
    };
}

function createBindingDraft(): BindingDraft {
    return {
        draftKey: nextDraftKey(),
        bindingType: "ONTOLOGY",
        bindingOrder: 0,
    };
}

function toScopeDraft(scope: ArchiveGovernanceScopeDto): ScopeDraft {
    return {
        draftKey: nextDraftKey(),
        id: scope.id,
        scopeType: scope.scopeType,
        fondsCode: scope.fondsCode,
        categoryCode: scope.categoryCode,
        defaultFlag: scope.defaultFlag,
    };
}

function toBindingDraft(binding: ArchiveGovernanceBindingDto): BindingDraft {
    return {
        draftKey: nextDraftKey(),
        id: binding.id,
        bindingType: binding.bindingType,
        targetType: binding.targetType,
        targetId: binding.targetId,
        targetCode: binding.targetCode,
        bindingOrder: binding.bindingOrder,
    };
}

function normalizeScopeTypeChange(
    scope: ScopeDraft,
    scopeType: ArchiveGovernanceScopeType,
): Partial<ScopeDraft> {
    if (scopeType === "GLOBAL") {
        return { scopeType, fondsCode: undefined, categoryCode: undefined };
    }
    if (scopeType === "FONDS") {
        return { scopeType, categoryCode: undefined };
    }
    return { scopeType, fondsCode: undefined };
}

function toScopeRequest(scope: ScopeDraft): ArchiveGovernanceScopeRequest {
    if (scope.scopeType === "GLOBAL") {
        return {
            scopeType: scope.scopeType,
            defaultFlag: scope.defaultFlag,
        };
    }
    if (scope.scopeType === "FONDS") {
        return {
            scopeType: scope.scopeType,
            fondsCode: trimToUndefined(scope.fondsCode),
            defaultFlag: scope.defaultFlag,
        };
    }
    return {
        scopeType: scope.scopeType,
        categoryCode: trimToUndefined(scope.categoryCode),
        defaultFlag: scope.defaultFlag,
    };
}

function toBindingRequest(binding: BindingDraft): ArchiveGovernanceBindingRequest {
    return {
        bindingType: binding.bindingType,
        targetType: trimToUndefined(binding.targetType),
        targetId: binding.targetId,
        targetCode: trimToUndefined(binding.targetCode),
        bindingOrder: binding.bindingOrder,
    };
}

function trimToUndefined(value: string | undefined) {
    const trimmed = value?.trim();
    return trimmed ? trimmed : undefined;
}

function toOptionalNumber(value: string | number | null) {
    if (typeof value === "number") {
        return value;
    }
    if (typeof value === "string" && value.trim() !== "") {
        return Number(value);
    }
    return undefined;
}
