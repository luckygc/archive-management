import { DeleteOutlined, PlusOutlined } from "@ant-design/icons";
import { Button, Col, DatePicker, Empty, Form, Input, InputNumber, Row, Select, Space } from "antd";
import type { FormInstance } from "antd";
import { useEffect } from "react";

import type {
    ArchiveCategoryDto,
    ArchiveFieldDto,
    ArchiveItemQueryOperator,
    ArchiveItemRelationDirection,
    ArchiveRelatedFilterCategoryDto,
} from "@/shared/types/archive";

export interface QueryConditionDraft {
    fieldCode?: string;
    op?: ArchiveItemQueryOperator;
    value?: unknown;
    startValue?: unknown;
    endValue?: unknown;
}

export interface RelatedGroupDraft {
    categoryId?: number;
    direction?: ArchiveItemRelationDirection;
    conditions?: QueryConditionDraft[];
}

export interface ArchiveQueryFormValues {
    categoryId?: number;
    fondsCode?: string;
    keyword?: string;
    conditions?: QueryConditionDraft[];
    relatedGroups?: RelatedGroupDraft[];
}

interface ArchiveAdvancedQueryPanelProps {
    form: FormInstance<ArchiveQueryFormValues>;
    categories: ArchiveCategoryDto[];
    fields: ArchiveFieldDto[];
    relatedCategories: ArchiveRelatedFilterCategoryDto[];
    relatedFieldsByCategory: Map<number, ArchiveFieldDto[]>;
    showKeyword: boolean;
    submitting?: boolean;
    onSubmit: (values: ArchiveQueryFormValues) => void;
    onReset: () => void;
}

export function ArchiveAdvancedQueryPanel({
    form,
    categories,
    fields,
    relatedCategories,
    relatedFieldsByCategory,
    showKeyword,
    submitting = false,
    onSubmit,
    onReset,
}: ArchiveAdvancedQueryPanelProps) {
    useEffect(() => {
        const currentGroups = form.getFieldValue("relatedGroups") as
            | RelatedGroupDraft[]
            | undefined;
        form.setFieldValue(
            "relatedGroups",
            relatedCategories.map((category) => {
                const current = currentGroups?.find(
                    (group) =>
                        group.categoryId === category.categoryId &&
                        group.direction === category.direction,
                );
                return {
                    categoryId: category.categoryId,
                    direction: category.direction,
                    conditions: current?.conditions ?? [],
                };
            }),
        );
    }, [form, relatedCategories]);

    return (
        <Form form={form} layout="vertical" onFinish={onSubmit}>
            <Row gutter={12}>
                <Col span={6}>
                    <Form.Item
                        label="档案分类"
                        name="categoryId"
                        rules={[{ required: true, message: "请选择档案分类" }]}
                    >
                        <Select
                            showSearch={{ optionFilterProp: "label" }}
                            options={categories.map((category) => ({
                                label: category.categoryName,
                                value: category.id,
                            }))}
                            placeholder="选择分类"
                        />
                    </Form.Item>
                </Col>
                <Col span={5}>
                    <Form.Item label="全宗号" name="fondsCode">
                        <Input allowClear placeholder="按全宗号过滤" />
                    </Form.Item>
                </Col>
                {showKeyword ? (
                    <Col span={7}>
                        <Form.Item label="全文关键词" name="keyword">
                            <Input allowClear placeholder="在全文投影中检索" />
                        </Form.Item>
                    </Col>
                ) : null}
                <Col flex="auto">
                    <Form.Item label=" " colon={false}>
                        <Space>
                            <Button htmlType="submit" loading={submitting} type="primary">
                                查询
                            </Button>
                            <Button onClick={onReset}>重置</Button>
                        </Space>
                    </Form.Item>
                </Col>
            </Row>

            <Form.List name="conditions">
                {(items, { add, remove }) => (
                    <div className="am-query-section">
                        <div className="am-query-section__toolbar">
                            <span>本分类条件</span>
                            <Button
                                icon={<PlusOutlined />}
                                size="small"
                                type="link"
                                onClick={() => add({ op: "EQ" })}
                            >
                                添加条件
                            </Button>
                        </div>
                        {items.map((item) => (
                            <ConditionRow
                                key={item.key}
                                fields={fields}
                                name={item.name}
                                onRemove={() => remove(item.name)}
                            />
                        ))}
                    </div>
                )}
            </Form.List>

            <Form.List name="relatedGroups">
                {(groups) => (
                    <div className="am-query-section">
                        <div className="am-query-section__toolbar">
                            <span>关联分类条件</span>
                        </div>
                        {relatedCategories.length === 0 ? (
                            <Empty
                                description="当前分类暂无可用关联筛选分类"
                                image={Empty.PRESENTED_IMAGE_SIMPLE}
                            />
                        ) : null}
                        {groups.map((group, index) => {
                            const relatedCategory = relatedCategories[index];
                            if (!relatedCategory) {
                                return null;
                            }
                            return (
                                <RelatedGroup
                                    key={`${relatedCategory.categoryId}:${relatedCategory.direction}`}
                                    name={group.name}
                                    relatedCategory={relatedCategory}
                                    relatedFieldsByCategory={relatedFieldsByCategory}
                                />
                            );
                        })}
                    </div>
                )}
            </Form.List>
        </Form>
    );
}

function RelatedGroup({
    name,
    relatedCategory,
    relatedFieldsByCategory,
}: {
    name: number;
    relatedCategory: ArchiveRelatedFilterCategoryDto;
    relatedFieldsByCategory: Map<number, ArchiveFieldDto[]>;
}) {
    const fields = relatedFieldsByCategory.get(relatedCategory.categoryId) ?? [];

    return (
        <div className="am-query-related-group">
            <Form.Item hidden name={[name, "categoryId"]}>
                <Input />
            </Form.Item>
            <Form.Item hidden name={[name, "direction"]}>
                <Input />
            </Form.Item>
            <div className="am-query-related-group__header">
                <span>{relatedCategory.categoryName}</span>
                <span>{relationDirectionLabel(relatedCategory.direction)}</span>
            </div>
            <Form.List name={[name, "conditions"]}>
                {(items, { add, remove }) => (
                    <>
                        {items.map((item) => (
                            <ConditionRow
                                fields={fields}
                                key={item.key}
                                name={item.name}
                                onRemove={() => remove(item.name)}
                            />
                        ))}
                        <Button
                            icon={<PlusOutlined />}
                            size="small"
                            type="dashed"
                            onClick={() => add({ op: "EQ" })}
                        >
                            添加关联字段条件
                        </Button>
                    </>
                )}
            </Form.List>
        </div>
    );
}

function relationDirectionLabel(direction: ArchiveItemRelationDirection) {
    return direction === "BOTH" ? "双向关联" : "当前关联出去";
}

function ConditionRow({
    fields,
    name,
    onRemove,
}: {
    fields: ArchiveFieldDto[];
    name: number;
    onRemove: () => void;
}) {
    const prefix = [name];

    return (
        <Row className="am-query-condition" gutter={8}>
            <Col span={7}>
                <Form.Item name={[...prefix, "fieldCode"]} rules={[{ required: true }]}>
                    <Select
                        showSearch={{ optionFilterProp: "label" }}
                        options={fields
                            .filter(
                                (field) =>
                                    field.enabled &&
                                    field.archiveLevel === "ITEM" &&
                                    field.exactSearchable,
                            )
                            .map((field) => ({
                                label: field.fieldName,
                                value: field.fieldCode,
                            }))}
                        placeholder="字段"
                    />
                </Form.Item>
            </Col>
            <Col span={5}>
                <Form.Item name={[...prefix, "op"]}>
                    <Select options={operatorOptions} />
                </Form.Item>
            </Col>
            <Col span={9}>
                <Form.Item shouldUpdate noStyle>
                    {({ getFieldValue }) => {
                        const op = getFieldValue([...prefix, "op"]) as
                            | ArchiveItemQueryOperator
                            | undefined;
                        const fieldCode = getFieldValue([...prefix, "fieldCode"]) as
                            | string
                            | undefined;
                        const field = fields.find((item) => item.fieldCode === fieldCode);
                        if (op === "BETWEEN") {
                            return (
                                <Space.Compact block>
                                    <Form.Item name={[...prefix, "startValue"]} noStyle>
                                        {renderValueControl(field, "开始值")}
                                    </Form.Item>
                                    <Form.Item name={[...prefix, "endValue"]} noStyle>
                                        {renderValueControl(field, "结束值")}
                                    </Form.Item>
                                </Space.Compact>
                            );
                        }
                        if (op === "IS_EMPTY" || op === "IS_NOT_EMPTY") {
                            return <Input disabled placeholder="无需输入值" />;
                        }
                        return (
                            <Form.Item name={[...prefix, "value"]} noStyle>
                                {renderValueControl(field, "查询值")}
                            </Form.Item>
                        );
                    }}
                </Form.Item>
            </Col>
            <Col span={3}>
                <Button danger icon={<DeleteOutlined />} onClick={onRemove} />
            </Col>
        </Row>
    );
}

const operatorOptions: Array<{ label: string; value: ArchiveItemQueryOperator }> = [
    { label: "等于", value: "EQ" },
    { label: "包含", value: "CONTAINS" },
    { label: "开头是", value: "STARTS_WITH" },
    { label: "大于等于", value: "GTE" },
    { label: "小于等于", value: "LTE" },
    { label: "区间", value: "BETWEEN" },
    { label: "为空", value: "IS_EMPTY" },
    { label: "不为空", value: "IS_NOT_EMPTY" },
];

function renderValueControl(field: ArchiveFieldDto | undefined, placeholder: string) {
    if (field?.fieldType === "INTEGER") {
        return <InputNumber placeholder={placeholder} precision={0} style={{ width: "100%" }} />;
    }
    if (field?.fieldType === "DECIMAL") {
        return (
            <InputNumber
                placeholder={placeholder}
                precision={field.decimalScale ?? 2}
                style={{ width: "100%" }}
            />
        );
    }
    if (field?.fieldType === "DATE" || field?.fieldType === "DATETIME") {
        return (
            <DatePicker
                format={field.fieldType === "DATETIME" ? "YYYY-MM-DD HH:mm:ss" : "YYYY-MM-DD"}
                placeholder={placeholder}
                showTime={field.fieldType === "DATETIME"}
                style={{ width: "100%" }}
            />
        );
    }
    return <Input placeholder={placeholder} />;
}
