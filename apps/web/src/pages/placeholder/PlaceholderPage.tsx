import { Card, Typography } from "antd";

export function PlaceholderPage({ title }: { title: string }) {
    return (
        <section className="am-page">
            <div className="am-page__header">
                <Typography.Title level={1}>{title}</Typography.Title>
            </div>
            <Card>
                <Typography.Text type="secondary">该功能入口已接入 React 主壳。</Typography.Text>
            </Card>
        </section>
    );
}
