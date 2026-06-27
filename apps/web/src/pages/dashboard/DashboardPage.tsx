import {
    CheckCircleOutlined,
    ClockCircleOutlined,
    DatabaseOutlined,
    InboxOutlined,
} from "@ant-design/icons";
import { Card, Col, Row, Statistic, Table, Tag, Typography } from "antd";
import type { TableColumnsType } from "antd";

interface TodoItem {
    key: string;
    title: string;
    module: string;
    status: "待处理" | "处理中" | "已完成";
}

const todos: TodoItem[] = [
    { key: "1", title: "财务凭证归档批次待确认", module: "移交接收", status: "待处理" },
    { key: "2", title: "项目档案字段布局待发布", module: "目录配置", status: "处理中" },
    { key: "3", title: "历史系统同步目录已完成", module: "同步接入", status: "已完成" },
];

const columns: TableColumnsType<TodoItem> = [
    { title: "事项", dataIndex: "title", key: "title" },
    { title: "模块", dataIndex: "module", key: "module", width: 120 },
    {
        title: "状态",
        dataIndex: "status",
        key: "status",
        width: 110,
        render: (status: TodoItem["status"]) => {
            const color = status === "已完成" ? "green" : status === "处理中" ? "blue" : "gold";
            return <Tag color={color}>{status}</Tag>;
        },
    },
];

export function DashboardPage() {
    return (
        <section className="am-page">
            <div className="am-page__header">
                <Typography.Title level={1}>工作台</Typography.Title>
            </div>
            <Row gutter={[16, 16]}>
                <Col xs={24} md={12} xl={6}>
                    <Card>
                        <Statistic prefix={<DatabaseOutlined />} title="档案记录" value={1280} />
                    </Card>
                </Col>
                <Col xs={24} md={12} xl={6}>
                    <Card>
                        <Statistic prefix={<InboxOutlined />} title="待接收批次" value={8} />
                    </Card>
                </Col>
                <Col xs={24} md={12} xl={6}>
                    <Card>
                        <Statistic prefix={<ClockCircleOutlined />} title="处理中任务" value={16} />
                    </Card>
                </Col>
                <Col xs={24} md={12} xl={6}>
                    <Card>
                        <Statistic prefix={<CheckCircleOutlined />} title="今日完成" value={42} />
                    </Card>
                </Col>
                <Col span={24}>
                    <Card title="近期事项">
                        <Table<TodoItem>
                            columns={columns}
                            dataSource={todos}
                            pagination={false}
                            rowKey="key"
                            size="middle"
                        />
                    </Card>
                </Col>
            </Row>
        </section>
    );
}
