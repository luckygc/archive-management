import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
    Button,
    Card,
    Form,
    Input,
    InputNumber,
    message,
    Modal,
    Select,
    Space,
    Switch,
    Table,
    Tag,
    Typography,
} from "antd";
import type { TableColumnsType } from "antd";
import { useState } from "react";

import {
    createArchiveRule,
    disableArchiveRule,
    enableArchiveRule,
    executeArchiveRules,
    listArchiveRules,
    publishArchiveRule,
} from "@/shared/api/archive";
import type {
    ArchiveRuleDecisionDto,
    ArchiveRuleDto,
    ArchiveRuleEffectType,
    ArchiveRuleStatus,
    ArchiveRuleType,
} from "@/shared/types/archive";

const ruleTypes: ArchiveRuleType[] = [
    "VALIDATION",
    "DERIVATION",
    "REFERENCE_CODE",
    "RETENTION",
    "ACCESS",
    "QUALITY",
    "TRANSFER",
    "FILING",
    "EXPORT",
];

const effectTypes: ArchiveRuleEffectType[] = [
    "VALIDATION_ERROR",
    "WARNING",
    "SUGGEST_VALUE",
    "DERIVED_VALUE",
    "REQUIRE_REVIEW",
    "REQUIRE_QUALITY_CHECK",
    "DENY_ACCESS",
    "MASK_FIELD",
    "INCLUDE_IN_PACKAGE",
];

export function ArchiveRulesPage() {
    const queryClient = useQueryClient();
    const [schemeVersionId, setSchemeVersionId] = useState<number>();
    const [status, setStatus] = useState<ArchiveRuleStatus>();
    const [ruleModalOpen, setRuleModalOpen] = useState(false);
    const [executeModalOpen, setExecuteModalOpen] = useState(false);
    const [decisions, setDecisions] = useState<ArchiveRuleDecisionDto[]>([]);
    const [ruleForm] = Form.useForm();
    const [executeForm] = Form.useForm();

    const rulesQuery = useQuery({
        queryKey: ["archive-rules", schemeVersionId, status],
        queryFn: () => listArchiveRules(schemeVersionId!, status),
        enabled: schemeVersionId != null,
    });

    const createMutation = useMutation({
        mutationFn: createArchiveRule,
        onSuccess: () => {
            message.success("规则已创建");
            setRuleModalOpen(false);
            ruleForm.resetFields();
            queryClient.invalidateQueries({ queryKey: ["archive-rules"] });
        },
        onError: (error: Error) => message.error(error.message),
    });

    const publishMutation = useMutation({
        mutationFn: publishArchiveRule,
        onSuccess: () => {
            message.success("规则已发布");
            queryClient.invalidateQueries({ queryKey: ["archive-rules"] });
        },
        onError: (error: Error) => message.error(error.message),
    });

    const enabledMutation = useMutation({
        mutationFn: ({ id, enabled }: { id: number; enabled: boolean }) =>
            enabled ? enableArchiveRule(id) : disableArchiveRule(id),
        onSuccess: () => queryClient.invalidateQueries({ queryKey: ["archive-rules"] }),
        onError: (error: Error) => message.error(error.message),
    });

    const executeMutation = useMutation({
        mutationFn: executeArchiveRules,
        onSuccess: (response) => {
            setDecisions(response.items);
            message.success("规则试算完成");
        },
        onError: (error: Error) => message.error(error.message),
    });

    async function submitRule() {
        const values = await ruleForm.validateFields();
        createMutation.mutate({
            schemeVersionId: values.schemeVersionId,
            ruleCode: values.ruleCode,
            ruleName: values.ruleName,
            ruleType: values.ruleType,
            triggerCode: values.triggerCode,
            scopeFondsCode: values.scopeFondsCode,
            scopeCategoryCode: values.scopeCategoryCode,
            scopeArchiveLevel: values.scopeArchiveLevel,
            priority: values.priority ?? 0,
            conditionJson: parseJson(values.conditionJson),
            enabled: values.enabled ?? true,
            effects: [
                {
                    effectType: values.effectType,
                    effectOrder: 0,
                    effectParams: parseJson(values.effectParams || "{}"),
                },
            ],
        });
    }

    async function submitExecute() {
        const values = await executeForm.validateFields();
        executeMutation.mutate({
            schemeVersionId: values.schemeVersionId,
            triggerCode: values.triggerCode,
            fondsCode: values.fondsCode,
            categoryCode: values.categoryCode,
            objectTypeCode: values.objectTypeCode,
            archiveLevel: values.archiveLevel,
            facts: parseJson(values.facts),
            includeSkipped: values.includeSkipped ?? false,
            recordTrace: values.recordTrace ?? false,
        });
    }

    const columns: TableColumnsType<ArchiveRuleDto> = [
        { title: "编码", dataIndex: "ruleCode", width: 180 },
        { title: "名称", dataIndex: "ruleName" },
        { title: "类型", dataIndex: "ruleType", width: 130 },
        { title: "触发点", dataIndex: "triggerCode", width: 130 },
        { title: "优先级", dataIndex: "priority", width: 90 },
        {
            title: "状态",
            dataIndex: "status",
            width: 100,
            render: (value: ArchiveRuleStatus) => (
                <Tag color={value === "PUBLISHED" ? "blue" : undefined}>
                    {value === "PUBLISHED" ? "已发布" : "草稿"}
                </Tag>
            ),
        },
        {
            title: "启用",
            dataIndex: "enabled",
            width: 90,
            render: (enabled: boolean, row) => (
                <Switch
                    checked={enabled}
                    size="small"
                    onChange={(checked) => enabledMutation.mutate({ id: row.id, enabled: checked })}
                />
            ),
        },
        {
            title: "操作",
            width: 110,
            render: (_, row) => (
                <Button
                    disabled={row.status !== "DRAFT"}
                    size="small"
                    onClick={() => publishMutation.mutate(row.id)}
                >
                    发布
                </Button>
            ),
        },
    ];

    return (
        <section className="am-page">
            <div className="am-page__header">
                <Typography.Title level={1}>本地规则</Typography.Title>
                <Space>
                    <InputNumber
                        min={1}
                        placeholder="治理版本 ID"
                        value={schemeVersionId}
                        onChange={(value) => setSchemeVersionId(value ?? undefined)}
                    />
                    <Select
                        allowClear
                        placeholder="状态"
                        style={{ width: 120 }}
                        value={status}
                        options={[
                            { label: "草稿", value: "DRAFT" },
                            { label: "已发布", value: "PUBLISHED" },
                        ]}
                        onChange={setStatus}
                    />
                    <Button type="primary" onClick={() => setRuleModalOpen(true)}>
                        新建规则
                    </Button>
                    <Button onClick={() => setExecuteModalOpen(true)}>规则试算</Button>
                </Space>
            </div>
            <Card>
                <Table
                    columns={columns}
                    dataSource={rulesQuery.data?.items ?? []}
                    loading={rulesQuery.isLoading}
                    pagination={false}
                    rowKey="id"
                />
            </Card>
            <Modal
                open={ruleModalOpen}
                title="新建规则"
                width={720}
                onCancel={() => setRuleModalOpen(false)}
                onOk={() => void submitRule()}
            >
                <Form
                    form={ruleForm}
                    layout="vertical"
                    initialValues={{
                        ruleType: "VALIDATION",
                        effectType: "VALIDATION_ERROR",
                        enabled: true,
                        priority: 0,
                        conditionJson:
                            '{"field":"fixed.archiveYear","operator":"GTE","value":2020}',
                        effectParams: '{"message":"规则命中"}',
                    }}
                >
                    <Form.Item
                        label="治理版本 ID"
                        name="schemeVersionId"
                        rules={[{ required: true }]}
                    >
                        <InputNumber min={1} style={{ width: "100%" }} />
                    </Form.Item>
                    <Form.Item label="规则编码" name="ruleCode" rules={[{ required: true }]}>
                        <Input />
                    </Form.Item>
                    <Form.Item label="规则名称" name="ruleName" rules={[{ required: true }]}>
                        <Input />
                    </Form.Item>
                    <Form.Item label="规则类型" name="ruleType">
                        <Select options={ruleTypes.map((value) => ({ label: value, value }))} />
                    </Form.Item>
                    <Form.Item label="触发点" name="triggerCode" rules={[{ required: true }]}>
                        <Input placeholder="BEFORE_SAVE" />
                    </Form.Item>
                    <Form.Item label="优先级" name="priority">
                        <InputNumber style={{ width: "100%" }} />
                    </Form.Item>
                    <Form.Item
                        label="条件树 JSON"
                        name="conditionJson"
                        rules={[{ required: true }]}
                    >
                        <Input.TextArea rows={5} />
                    </Form.Item>
                    <Form.Item label="effect 类型" name="effectType">
                        <Select options={effectTypes.map((value) => ({ label: value, value }))} />
                    </Form.Item>
                    <Form.Item label="effect 参数 JSON" name="effectParams">
                        <Input.TextArea rows={3} />
                    </Form.Item>
                    <Form.Item label="启用" name="enabled" valuePropName="checked">
                        <Switch />
                    </Form.Item>
                </Form>
            </Modal>
            <Modal
                open={executeModalOpen}
                title="规则试算"
                width={760}
                onCancel={() => setExecuteModalOpen(false)}
                onOk={() => void submitExecute()}
            >
                <Form
                    form={executeForm}
                    layout="vertical"
                    initialValues={{
                        schemeVersionId,
                        triggerCode: "BEFORE_SAVE",
                        objectTypeCode: "ARCHIVE_ITEM",
                        archiveLevel: "ITEM",
                        facts: '{"fixed.archiveYear":2026}',
                        includeSkipped: false,
                        recordTrace: false,
                    }}
                >
                    <Form.Item
                        label="治理版本 ID"
                        name="schemeVersionId"
                        rules={[{ required: true }]}
                    >
                        <InputNumber min={1} style={{ width: "100%" }} />
                    </Form.Item>
                    <Form.Item label="触发点" name="triggerCode" rules={[{ required: true }]}>
                        <Input />
                    </Form.Item>
                    <Form.Item label="对象类型" name="objectTypeCode">
                        <Input />
                    </Form.Item>
                    <Form.Item label="对象层级" name="archiveLevel">
                        <Select
                            options={[
                                { label: "条目", value: "ITEM" },
                                { label: "案卷", value: "VOLUME" },
                            ]}
                        />
                    </Form.Item>
                    <Form.Item label="事实 JSON" name="facts" rules={[{ required: true }]}>
                        <Input.TextArea rows={5} />
                    </Form.Item>
                    <Form.Item label="返回未命中" name="includeSkipped" valuePropName="checked">
                        <Switch />
                    </Form.Item>
                    <Form.Item label="保存追踪" name="recordTrace" valuePropName="checked">
                        <Switch />
                    </Form.Item>
                </Form>
                <Table
                    columns={[
                        { title: "规则", dataIndex: "ruleCode" },
                        {
                            title: "命中",
                            dataIndex: "matched",
                            width: 80,
                            render: (v) => (v ? "是" : "否"),
                        },
                        { title: "级别", dataIndex: "severity", width: 90 },
                        {
                            title: "阻断",
                            dataIndex: "blocking",
                            width: 80,
                            render: (v) => (v ? "是" : "否"),
                        },
                        { title: "消息", dataIndex: "message" },
                    ]}
                    dataSource={decisions}
                    pagination={false}
                    rowKey={(row) => `${row.ruleId ?? row.ruleCode}-${row.matched}`}
                    size="small"
                />
            </Modal>
        </section>
    );
}

function parseJson(value: string) {
    try {
        return JSON.parse(value || "{}") as Record<string, unknown>;
    } catch {
        throw new Error("JSON 格式不正确");
    }
}
