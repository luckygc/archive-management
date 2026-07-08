import { Alert, Card, Descriptions, Empty, Space, Spin, Typography } from "antd";
import { useQuery } from "@tanstack/react-query";

import { getIntakeOverview } from "@/shared/api/intake";

export function IntakePage() {
    const { data, error, isLoading } = useQuery({
        queryKey: ["intake-overview"],
        queryFn: getIntakeOverview,
    });

    if (isLoading) {
        return (
            <main className="am-page">
                <Spin />
            </main>
        );
    }

    if (error) {
        return (
            <main className="am-page">
                <Alert title="归档接收入口加载失败" showIcon type="error" />
            </main>
        );
    }

    return (
        <main className="am-page">
            <Space orientation="vertical" size={16} style={{ width: "100%" }}>
                <Card>
                    <Descriptions column={1} size="small" title="归档接收">
                        <Descriptions.Item label="外部连接">
                            {data?.externalConnectionConfigured ? "已配置" : "未配置"}
                        </Descriptions.Item>
                        <Descriptions.Item label="当前状态">{data?.status}</Descriptions.Item>
                        <Descriptions.Item label="说明">{data?.message}</Descriptions.Item>
                    </Descriptions>
                </Card>
                <Empty
                    description={
                        <Typography.Text type="secondary">
                            数据源接入、清洗、字段映射和暂存处理入口已预留
                        </Typography.Text>
                    }
                />
            </Space>
        </main>
    );
}
