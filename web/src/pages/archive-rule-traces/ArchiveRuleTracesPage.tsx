import { useMutation } from "@tanstack/react-query";
import {
    Button,
    Card,
    Form,
    Input,
    InputNumber,
    message,
    Select,
    Space,
    Table,
    Tag,
    Typography,
} from "antd";
import type { TableColumnsType } from "antd";
import { useState } from "react";

import { searchArchiveRuleTraces } from "@/shared/api/archive";
import type { ArchiveRuleTraceDto, ArchiveRuleType } from "@/shared/types/archive";

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

export function ArchiveRuleTracesPage() {
    const [form] = Form.useForm();
    const [items, setItems] = useState<ArchiveRuleTraceDto[]>([]);
    const searchMutation = useMutation({
        mutationFn: searchArchiveRuleTraces,
        onSuccess: (response) => setItems(response.items),
        onError: (error: Error) => message.error(error.message),
    });

    async function submitSearch() {
        const values = await form.validateFields();
        searchMutation.mutate({
            schemeVersionId: values.schemeVersionId,
            triggerCode: values.triggerCode,
            objectTypeCode: values.objectTypeCode,
            objectId: values.objectId,
            ruleType: values.ruleType,
            limit: values.limit ?? 100,
        });
    }

    const columns: TableColumnsType<ArchiveRuleTraceDto> = [
        { title: "时间", dataIndex: "createdAt", width: 170 },
        { title: "治理版本", dataIndex: "schemeVersionId", width: 100 },
        { title: "触发点", dataIndex: "triggerCode", width: 130 },
        { title: "对象类型", dataIndex: "objectTypeCode", width: 130 },
        { title: "对象 ID", dataIndex: "objectId", width: 100 },
        { title: "规则", dataIndex: "ruleCode", width: 160 },
        { title: "类型", dataIndex: "ruleType", width: 120 },
        {
            title: "结果",
            dataIndex: "matchedFlag",
            width: 90,
            render: (matched: boolean, row) => (
                <Tag color={row.blockingFlag ? "red" : matched ? "blue" : undefined}>
                    {row.blockingFlag ? "阻断" : matched ? "命中" : "跳过"}
                </Tag>
            ),
        },
        { title: "消息", dataIndex: "message" },
    ];

    return (
        <section className="am-page">
            <div className="am-page__header">
                <Typography.Title level={1}>规则追踪</Typography.Title>
            </div>
            <Card className="am-page__filter">
                <Form form={form} layout="inline" initialValues={{ limit: 100 }}>
                    <Form.Item label="治理版本" name="schemeVersionId">
                        <InputNumber min={1} />
                    </Form.Item>
                    <Form.Item label="触发点" name="triggerCode">
                        <Input allowClear />
                    </Form.Item>
                    <Form.Item label="对象类型" name="objectTypeCode">
                        <Input allowClear />
                    </Form.Item>
                    <Form.Item label="对象 ID" name="objectId">
                        <InputNumber min={1} />
                    </Form.Item>
                    <Form.Item label="规则类型" name="ruleType">
                        <Select
                            allowClear
                            options={ruleTypes.map((value) => ({ label: value, value }))}
                            style={{ width: 140 }}
                        />
                    </Form.Item>
                    <Form.Item label="条数" name="limit">
                        <InputNumber max={500} min={1} />
                    </Form.Item>
                    <Form.Item>
                        <Space>
                            <Button
                                loading={searchMutation.isPending}
                                type="primary"
                                onClick={() => void submitSearch()}
                            >
                                查询
                            </Button>
                            <Button onClick={() => form.resetFields()}>重置</Button>
                        </Space>
                    </Form.Item>
                </Form>
            </Card>
            <Card>
                <Table
                    columns={columns}
                    dataSource={items}
                    loading={searchMutation.isPending}
                    pagination={false}
                    rowKey="id"
                />
            </Card>
        </section>
    );
}
