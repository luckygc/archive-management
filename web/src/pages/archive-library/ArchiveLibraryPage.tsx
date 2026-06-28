import { Button, Card, Form, Input, Select, Space, Table, Tag, Typography } from "antd";
import type { TableColumnsType } from "antd";

interface ArchiveRecordRow {
    id: number;
    archiveNo: string;
    title: string;
    category: string;
    status: "已入库" | "待整理" | "待同步";
    owner: string;
}

const records: ArchiveRecordRow[] = [
    {
        id: 190001,
        archiveNo: "AM-2026-001",
        title: "项目建设审批材料",
        category: "项目档案",
        status: "已入库",
        owner: "档案室",
    },
    {
        id: 190002,
        archiveNo: "AM-2026-002",
        title: "财务凭证归档批次",
        category: "会计档案",
        status: "待整理",
        owner: "财务部",
    },
    {
        id: 190003,
        archiveNo: "AM-2026-003",
        title: "外部系统同步记录",
        category: "异构数据归档",
        status: "待同步",
        owner: "信息中心",
    },
];

const columns: TableColumnsType<ArchiveRecordRow> = [
    { title: "档号", dataIndex: "archiveNo", key: "archiveNo", width: 150 },
    { title: "题名", dataIndex: "title", key: "title" },
    { title: "分类", dataIndex: "category", key: "category", width: 150 },
    {
        title: "状态",
        dataIndex: "status",
        key: "status",
        width: 110,
        render: (status: ArchiveRecordRow["status"]) => {
            const color = status === "已入库" ? "green" : status === "待整理" ? "gold" : "blue";
            return <Tag color={color}>{status}</Tag>;
        },
    },
    { title: "责任部门", dataIndex: "owner", key: "owner", width: 130 },
];

export function ArchiveLibraryPage() {
    return (
        <section className="am-page">
            <div className="am-page__header">
                <Typography.Title level={1}>档案库</Typography.Title>
                <Space>
                    <Button type="primary">新建档案</Button>
                    <Button>批量导入</Button>
                </Space>
            </div>
            <Card className="am-page__filter">
                <Form layout="inline">
                    <Form.Item label="关键词">
                        <Input placeholder="档号 / 题名" />
                    </Form.Item>
                    <Form.Item label="分类">
                        <Select
                            allowClear
                            placeholder="全部分类"
                            style={{ width: 180 }}
                            options={[
                                { label: "项目档案", value: "project" },
                                { label: "会计档案", value: "accounting" },
                                { label: "异构数据归档", value: "integration" },
                            ]}
                        />
                    </Form.Item>
                    <Form.Item label="备注">
                        <Input.TextArea aria-label="当前列表备注" rows={1} />
                    </Form.Item>
                    <Form.Item>
                        <Space>
                            <Button type="primary">查询</Button>
                            <Button>重置</Button>
                        </Space>
                    </Form.Item>
                </Form>
            </Card>
            <Card>
                <Table<ArchiveRecordRow>
                    columns={columns}
                    dataSource={records}
                    pagination={false}
                    rowKey="id"
                    scroll={{ x: 820, y: 520 }}
                    size="middle"
                    virtual
                />
            </Card>
        </section>
    );
}
