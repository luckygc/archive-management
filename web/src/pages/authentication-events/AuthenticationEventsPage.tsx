import { listAuthenticationEvents } from "@archive-management/frontend-core/api";
import type {
    AuthenticationEventDto,
    ListAuthenticationEventsParams,
} from "@archive-management/frontend-core/types";
import { SearchOutlined } from "@ant-design/icons";
import { useQuery } from "@tanstack/react-query";
import { Button, Card, DatePicker, Form, Input, Select, Space, Table, Tag, Typography } from "antd";
import type { TableColumnsType } from "antd";
import type { Dayjs } from "dayjs";
import { useState } from "react";

import { CursorPagination } from "@/shared/components/CursorPagination";

const { RangePicker } = DatePicker;

const eventTypeOptions = [
    { label: "登录成功", value: "login_success" },
    { label: "登录失败", value: "login_failure" },
    { label: "主动退出", value: "logout" },
    { label: "踢下线", value: "kickout" },
];

export function AuthenticationEventsPage() {
    const [limit, setLimit] = useState(100);
    const [cursor, setCursor] = useState<string | null>(null);
    const [filters, setFilters] = useState<ListAuthenticationEventsParams>({});
    const eventsQuery = useQuery({
        queryKey: ["authentication-events", limit, cursor, filters],
        queryFn: () =>
            listAuthenticationEvents({
                ...filters,
                limit,
                cursor,
                requestTotal: !cursor,
            }),
    });

    const columns: TableColumnsType<AuthenticationEventDto> = [
        {
            title: "事件",
            dataIndex: "eventType",
            key: "eventType",
            width: 120,
            render: (eventType: string) => (
                <Tag color={eventColor(eventType)}>{eventLabel(eventType)}</Tag>
            ),
        },
        { title: "账号", dataIndex: "username", key: "username", width: 150 },
        { title: "用户", dataIndex: "displayName", key: "displayName", width: 160 },
        {
            title: "操作人",
            dataIndex: "operatorUsername",
            key: "operatorUsername",
            width: 140,
            render: (value: string) => value || "-",
        },
        {
            title: "客户端",
            key: "client",
            width: 240,
            render: (_value, row) => (
                <Space direction="vertical" size={0}>
                    <span>{clientSummary(row)}</span>
                    <Typography.Text type="secondary">
                        {row.client.userAgent || "-"}
                    </Typography.Text>
                </Space>
            ),
        },
        {
            title: "请求",
            key: "request",
            width: 220,
            render: (_value, row) => (
                <Space direction="vertical" size={0}>
                    <span>{row.request.remoteAddress || "-"}</span>
                    <Typography.Text type="secondary">{row.request.host || "-"}</Typography.Text>
                </Space>
            ),
        },
        {
            title: "失败原因",
            dataIndex: "failureReason",
            key: "failureReason",
            width: 220,
            render: (value: string) => value || "-",
        },
        {
            title: "发生时间",
            dataIndex: "occurredAt",
            key: "occurredAt",
            width: 180,
            render: formatDateTime,
        },
    ];

    return (
        <section className="am-page">
            <div className="am-page__header">
                <Typography.Title level={1}>认证审计</Typography.Title>
                <Typography.Text type="secondary">
                    {eventsQuery.data?.total !== undefined && eventsQuery.data.total !== null
                        ? `共 ${eventsQuery.data.total} 条`
                        : ""}
                </Typography.Text>
            </div>
            <Card className="am-page__filter">
                <AuthenticationEventFilter
                    onSearch={(nextFilters) => {
                        setFilters(nextFilters);
                        setCursor(null);
                    }}
                />
            </Card>
            <Card className="am-page__result">
                <Table<AuthenticationEventDto>
                    columns={columns}
                    dataSource={eventsQuery.data?.items ?? []}
                    loading={eventsQuery.isLoading}
                    pagination={false}
                    rowKey={(row) => String(row.id)}
                    scroll={{ x: 1410 }}
                />
                <div className="am-table-footer">
                    <CursorPagination
                        limit={limit}
                        loading={eventsQuery.isFetching}
                        next={eventsQuery.data?.next}
                        prev={eventsQuery.data?.prev}
                        onLimitChange={(value) => {
                            setLimit(value);
                            setCursor(null);
                        }}
                        onPage={setCursor}
                    />
                </div>
            </Card>
        </section>
    );
}

function AuthenticationEventFilter({
    onSearch,
}: {
    onSearch: (filters: ListAuthenticationEventsParams) => void;
}) {
    const [form] = Form.useForm<{
        eventType?: string;
        username?: string;
        keyword?: string;
        occurredRange?: [Dayjs, Dayjs];
    }>();

    function submit() {
        const values = form.getFieldsValue();
        onSearch({
            eventType: values.eventType,
            username: values.username?.trim(),
            keyword: values.keyword?.trim(),
            occurredAfter: values.occurredRange?.[0]?.format("YYYY-MM-DDTHH:mm:ss"),
            occurredBefore: values.occurredRange?.[1]?.format("YYYY-MM-DDTHH:mm:ss"),
        });
    }

    function reset() {
        form.resetFields();
        onSearch({});
    }

    return (
        <Form form={form} layout="inline" onFinish={submit}>
            <Form.Item name="eventType">
                <Select
                    allowClear
                    placeholder="事件类型"
                    style={{ width: 140 }}
                    options={eventTypeOptions}
                />
            </Form.Item>
            <Form.Item name="username">
                <Input allowClear placeholder="账号" style={{ width: 160 }} />
            </Form.Item>
            <Form.Item name="keyword">
                <Input allowClear placeholder="关键字" style={{ width: 220 }} />
            </Form.Item>
            <Form.Item name="occurredRange">
                <RangePicker showTime />
            </Form.Item>
            <Form.Item>
                <Space>
                    <Button icon={<SearchOutlined />} type="primary" htmlType="submit">
                        查询
                    </Button>
                    <Button onClick={reset}>重置</Button>
                </Space>
            </Form.Item>
        </Form>
    );
}

function eventLabel(eventType: string) {
    return eventTypeOptions.find((option) => option.value === eventType)?.label ?? eventType;
}

function eventColor(eventType: string) {
    if (eventType === "login_success") {
        return "green";
    }
    if (eventType === "login_failure") {
        return "red";
    }
    if (eventType === "kickout") {
        return "orange";
    }
    return "default";
}

function clientSummary(row: AuthenticationEventDto) {
    return (
        [row.client.browserName, row.client.osName, row.client.deviceType]
            .filter(Boolean)
            .join(" / ") || "-"
    );
}

function formatDateTime(value?: string | null) {
    return value ? value.replace("T", " ") : "-";
}
