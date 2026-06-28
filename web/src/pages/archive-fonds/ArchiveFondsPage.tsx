import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Button, Card, Space, Switch, Table, Typography } from "antd";
import type { TableColumnsType } from "antd";

import { listArchiveFonds, updateArchiveFonds } from "@/shared/api/archive";
import type { ArchiveFondsDto, CollectionResponse } from "@/shared/types/archive";

const fondsQueryKey = ["archive-fonds"] as const;

export function ArchiveFondsPage() {
    const queryClient = useQueryClient();
    const fondsQuery = useQuery({
        queryKey: fondsQueryKey,
        queryFn: () => listArchiveFonds(),
    });
    const toggleMutation = useMutation({
        mutationFn: ({ enabled, row }: { enabled: boolean; row: ArchiveFondsDto }) =>
            updateArchiveFonds(row.id, {
                enabled,
                fondsCode: row.fondsCode,
                fondsName: row.fondsName,
                sortOrder: row.sortOrder,
            }),
        onSuccess: (updated) => {
            queryClient.setQueryData<CollectionResponse<ArchiveFondsDto>>(
                fondsQueryKey,
                (response) =>
                    response
                        ? {
                              ...response,
                              items: response.items.map((row) =>
                                  row.id === updated.id ? updated : row,
                              ),
                          }
                        : response,
            );
        },
    });

    const columns: TableColumnsType<ArchiveFondsDto> = [
        { title: "全宗号", dataIndex: "fondsCode", key: "fondsCode", width: 140 },
        { title: "全宗名称", dataIndex: "fondsName", key: "fondsName" },
        { title: "排序", dataIndex: "sortOrder", key: "sortOrder", width: 100 },
        {
            title: "启用",
            dataIndex: "enabled",
            key: "enabled",
            width: 120,
            render: (enabled: boolean, row) => (
                <Switch
                    aria-label={`${enabled ? "停用" : "启用"}全宗：${row.fondsName}`}
                    checked={enabled}
                    checkedChildren="启用"
                    loading={
                        toggleMutation.isPending && toggleMutation.variables?.row.id === row.id
                    }
                    size="small"
                    unCheckedChildren="停用"
                    onChange={(checked) => toggleMutation.mutate({ enabled: checked, row })}
                />
            ),
        },
    ];

    return (
        <section className="am-page">
            <div className="am-page__header">
                <Typography.Title level={1}>全宗管理</Typography.Title>
                <Space>
                    <Button type="primary">新建全宗</Button>
                </Space>
            </div>
            <Card>
                <Table<ArchiveFondsDto>
                    columns={columns}
                    dataSource={fondsQuery.data?.items ?? []}
                    loading={fondsQuery.isLoading}
                    pagination={false}
                    rowKey="id"
                />
            </Card>
        </section>
    );
}
