import { Button, Card, Col, Row, Space, Table, Tree, Typography } from "antd";
import type { DataNode } from "antd/es/tree";
import type { TableColumnsType } from "antd";

interface FieldRow {
    id: number;
    fieldCode: string;
    fieldName: string;
    fieldType: string;
    listVisible: boolean;
}

const treeData: DataNode[] = [
    {
        key: "archive",
        title: "档案分类",
        children: [
            { key: "project", title: "项目档案" },
            { key: "accounting", title: "会计档案" },
            { key: "integration", title: "异构数据归档" },
        ],
    },
];

const fields: FieldRow[] = [
    { id: 1, fieldCode: "archive_no", fieldName: "档号", fieldType: "文本", listVisible: true },
    { id: 2, fieldCode: "title", fieldName: "题名", fieldType: "长文本", listVisible: true },
    {
        id: 3,
        fieldCode: "archive_date",
        fieldName: "归档日期",
        fieldType: "日期",
        listVisible: true,
    },
];

const columns: TableColumnsType<FieldRow> = [
    { title: "字段编码", dataIndex: "fieldCode", key: "fieldCode", width: 180 },
    { title: "字段名称", dataIndex: "fieldName", key: "fieldName" },
    { title: "类型", dataIndex: "fieldType", key: "fieldType", width: 120 },
    {
        title: "列表显示",
        dataIndex: "listVisible",
        key: "listVisible",
        width: 120,
        render: (value: boolean) => (value ? "是" : "否"),
    },
];

export function ArchiveCategoriesPage() {
    return (
        <section className="am-page">
            <div className="am-page__header">
                <Typography.Title level={1}>档案分类</Typography.Title>
                <Space>
                    <Button type="primary">新建分类</Button>
                    <Button>生成动态表</Button>
                </Space>
            </div>
            <Row gutter={[16, 16]}>
                <Col xs={24} lg={7}>
                    <Card title="分类树">
                        <Tree blockNode defaultExpandAll treeData={treeData} />
                    </Card>
                </Col>
                <Col xs={24} lg={17}>
                    <Card title="字段定义">
                        <Table<FieldRow>
                            columns={columns}
                            dataSource={fields}
                            pagination={false}
                            rowKey="id"
                        />
                    </Card>
                </Col>
            </Row>
        </section>
    );
}
