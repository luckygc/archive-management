import { deleteLoginSession, listLoginSessions } from "@archive-management/frontend-core/api";
import type { LoginSessionDto } from "@archive-management/frontend-core/types";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Button, Card, Popconfirm, Space, Table, Tag, Typography, message } from "antd";
import type { TableColumnsType } from "antd";
import { useState } from "react";

import { CursorPagination } from "@/shared/components/CursorPagination";

const loginSessionsQueryKey = ["login-sessions"] as const;

export function LoginSessionsPage() {
    const queryClient = useQueryClient();
    const [messageApi, contextHolder] = message.useMessage();
    const [limit, setLimit] = useState(100);
    const [cursor, setCursor] = useState<string | null>(null);
    const sessionsQuery = useQuery({
        queryKey: [...loginSessionsQueryKey, limit, cursor],
        queryFn: () =>
            listLoginSessions({
                limit,
                cursor,
                requestTotal: !cursor,
            }),
    });
    const deleteMutation = useMutation({
        mutationFn: (sessionId: string) => deleteLoginSession(sessionId),
        onSuccess: async () => {
            await queryClient.invalidateQueries({ queryKey: loginSessionsQueryKey });
            messageApi.success("登录会话已踢下线");
        },
    });

    const columns: TableColumnsType<LoginSessionDto> = [
        {
            title: "用户",
            dataIndex: "displayName",
            key: "displayName",
            width: 180,
            render: (_value, row) => (
                <Space size={6}>
                    <span>{row.displayName || row.username}</span>
                    {row.current ? <Tag color="blue">当前</Tag> : null}
                </Space>
            ),
        },
        { title: "账号", dataIndex: "username", key: "username", width: 160 },
        {
            title: "客户端",
            key: "client",
            width: 260,
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
            title: "最后访问",
            dataIndex: "lastAccessTime",
            key: "lastAccessTime",
            width: 180,
            render: formatDateTime,
        },
        {
            title: "过期时间",
            dataIndex: "expiresAt",
            key: "expiresAt",
            width: 180,
            render: formatDateTime,
        },
        {
            title: "操作",
            key: "actions",
            fixed: "right",
            width: 110,
            render: (_value, row) =>
                row.current ? (
                    <Button disabled size="small">
                        当前会话
                    </Button>
                ) : (
                    <Popconfirm
                        title="踢下线该登录会话？"
                        okButtonProps={{ danger: true }}
                        okText="踢下线"
                        cancelText="取消"
                        onConfirm={() => deleteMutation.mutate(row.sessionId)}
                    >
                        <Button
                            danger
                            loading={deleteMutation.variables === row.sessionId}
                            size="small"
                        >
                            踢下线
                        </Button>
                    </Popconfirm>
                ),
        },
    ];

    return (
        <section className="am-page">
            {contextHolder}
            <div className="am-page__header">
                <Typography.Title level={1}>登录会话</Typography.Title>
                <Typography.Text type="secondary">
                    {sessionsQuery.data?.total !== undefined && sessionsQuery.data.total !== null
                        ? `共 ${sessionsQuery.data.total} 条`
                        : ""}
                </Typography.Text>
            </div>
            <Card>
                <Table<LoginSessionDto>
                    columns={columns}
                    dataSource={sessionsQuery.data?.items ?? []}
                    loading={sessionsQuery.isLoading}
                    pagination={false}
                    rowKey="sessionId"
                    scroll={{ x: 1180 }}
                />
                <div className="am-table-footer">
                    <CursorPagination
                        limit={limit}
                        loading={sessionsQuery.isFetching}
                        next={sessionsQuery.data?.next}
                        prev={sessionsQuery.data?.prev}
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

function clientSummary(row: LoginSessionDto) {
    return (
        [row.client.browserName, row.client.osName, row.client.deviceType]
            .filter(Boolean)
            .join(" / ") || "-"
    );
}

function formatDateTime(value?: string | null) {
    return value ? value.replace("T", " ") : "-";
}
