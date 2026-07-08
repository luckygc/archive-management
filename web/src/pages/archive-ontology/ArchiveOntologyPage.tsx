import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
    Button,
    Card,
    Form,
    Input,
    InputNumber,
    message,
    Modal,
    Popconfirm,
    Select,
    Space,
    Switch,
    Table,
    Tabs,
    Tag,
    Typography,
} from "antd";
import type { TableColumnsType } from "antd";
import { useState } from "react";

import {
    createArchiveOntologyAttributeType,
    createArchiveOntologyAttributeMapping,
    createArchiveOntologyEventType,
    createArchiveOntologyObjectType,
    createArchiveOntologyRelationType,
    deleteArchiveOntologyAttributeMapping,
    initializeArchiveOntologyEventTypes,
    initializeArchiveOntologyObjectTypes,
    listArchiveOntologyAttributeMappings,
    listArchiveOntologyAttributeTypes,
    listArchiveOntologyEventTypes,
    listArchiveOntologyObjectTypes,
    listArchiveOntologyRelationTypes,
} from "@/shared/api/archive";
import type {
    ArchiveOntologyAttributeDataType,
    ArchiveOntologyAttributeMappingDto,
    ArchiveOntologyAttributeTypeDto,
    ArchiveOntologyCardinality,
    ArchiveOntologyEventTypeDto,
    ArchiveOntologyMetadataDomain,
    ArchiveOntologyObjectTypeDto,
    ArchiveOntologyRelationCardinality,
    ArchiveOntologyRelationDirection,
    ArchiveOntologyRelationTypeDto,
} from "@/shared/types/archive";

const objectTypesKey = ["archive-ontology-object-types"] as const;
const attributesKey = ["archive-ontology-attribute-types"] as const;
const mappingsKey = ["archive-ontology-attribute-mappings"] as const;
const relationsKey = ["archive-ontology-relation-types"] as const;
const eventsKey = ["archive-ontology-event-types"] as const;

const dataTypes: ArchiveOntologyAttributeDataType[] = [
    "TEXT",
    "INTEGER",
    "DECIMAL",
    "DATE",
    "DATETIME",
    "BOOLEAN",
    "ENUM",
    "REFERENCE",
    "AMOUNT",
    "ORGANIZATION",
    "PERSON",
];

const domains: ArchiveOntologyMetadataDomain[] = [
    "DESCRIPTION",
    "STRUCTURE",
    "MANAGEMENT",
    "TECHNICAL",
    "ACCESS_USE",
    "PRESERVATION",
];

export function ArchiveOntologyPage() {
    const queryClient = useQueryClient();
    const [objectModalOpen, setObjectModalOpen] = useState(false);
    const [attributeModalOpen, setAttributeModalOpen] = useState(false);
    const [relationModalOpen, setRelationModalOpen] = useState(false);
    const [eventModalOpen, setEventModalOpen] = useState(false);
    const [mappingModalOpen, setMappingModalOpen] = useState(false);
    const [objectForm] = Form.useForm();
    const [attributeForm] = Form.useForm();
    const [relationForm] = Form.useForm();
    const [eventForm] = Form.useForm();
    const [mappingForm] = Form.useForm();

    const objectTypesQuery = useQuery({
        queryKey: objectTypesKey,
        queryFn: () => listArchiveOntologyObjectTypes(),
    });
    const attributesQuery = useQuery({
        queryKey: attributesKey,
        queryFn: () => listArchiveOntologyAttributeTypes(),
    });
    const mappingsQuery = useQuery({
        queryKey: mappingsKey,
        queryFn: () => listArchiveOntologyAttributeMappings(),
    });
    const relationsQuery = useQuery({
        queryKey: relationsKey,
        queryFn: () => listArchiveOntologyRelationTypes(),
    });
    const eventsQuery = useQuery({
        queryKey: eventsKey,
        queryFn: () => listArchiveOntologyEventTypes(),
    });

    const objectTypes = objectTypesQuery.data?.items ?? [];
    const objectOptions = objectTypes.map((item) => ({
        label: `${item.typeName}（${item.typeCode}）`,
        value: item.id,
    }));

    const createObjectMutation = useMutation({
        mutationFn: createArchiveOntologyObjectType,
        onSuccess: () => {
            message.success("对象类型已创建");
            setObjectModalOpen(false);
            objectForm.resetFields();
            queryClient.invalidateQueries({ queryKey: objectTypesKey });
        },
        onError: (error: Error) => message.error(error.message),
    });
    const initializeObjectsMutation = useMutation({
        mutationFn: initializeArchiveOntologyObjectTypes,
        onSuccess: () => {
            message.success("内置对象类型已初始化");
            queryClient.invalidateQueries({ queryKey: objectTypesKey });
        },
        onError: (error: Error) => message.error(error.message),
    });
    const createAttributeMutation = useMutation({
        mutationFn: createArchiveOntologyAttributeType,
        onSuccess: () => {
            message.success("属性类型已创建");
            setAttributeModalOpen(false);
            attributeForm.resetFields();
            queryClient.invalidateQueries({ queryKey: attributesKey });
        },
        onError: (error: Error) => message.error(error.message),
    });
    const createMappingMutation = useMutation({
        mutationFn: createArchiveOntologyAttributeMapping,
        onSuccess: () => {
            message.success("属性映射已创建");
            setMappingModalOpen(false);
            mappingForm.resetFields();
            queryClient.invalidateQueries({ queryKey: mappingsKey });
        },
        onError: (error: Error) => message.error(error.message),
    });
    const deleteMappingMutation = useMutation({
        mutationFn: deleteArchiveOntologyAttributeMapping,
        onSuccess: () => {
            message.success("属性映射已删除");
            queryClient.invalidateQueries({ queryKey: mappingsKey });
        },
        onError: (error: Error) => message.error(error.message),
    });
    const createRelationMutation = useMutation({
        mutationFn: createArchiveOntologyRelationType,
        onSuccess: () => {
            message.success("关系类型已创建");
            setRelationModalOpen(false);
            relationForm.resetFields();
            queryClient.invalidateQueries({ queryKey: relationsKey });
        },
        onError: (error: Error) => message.error(error.message),
    });
    const createEventMutation = useMutation({
        mutationFn: createArchiveOntologyEventType,
        onSuccess: () => {
            message.success("事件类型已创建");
            setEventModalOpen(false);
            eventForm.resetFields();
            queryClient.invalidateQueries({ queryKey: eventsKey });
        },
        onError: (error: Error) => message.error(error.message),
    });
    const initializeEventsMutation = useMutation({
        mutationFn: initializeArchiveOntologyEventTypes,
        onSuccess: () => {
            message.success("内置事件类型已初始化");
            queryClient.invalidateQueries({ queryKey: eventsKey });
        },
        onError: (error: Error) => message.error(error.message),
    });

    async function submitObject() {
        const values = await objectForm.validateFields();
        createObjectMutation.mutate({
            typeCode: values.typeCode,
            typeName: values.typeName,
            description: values.description,
            enabled: values.enabled ?? true,
        });
    }

    async function submitAttribute() {
        const values = await attributeForm.validateFields();
        createAttributeMutation.mutate({
            ...values,
            enabled: values.enabled ?? true,
            exactSearchable: values.exactSearchable ?? false,
            sortable: values.sortable ?? false,
            descriptionParticipating: values.descriptionParticipating ?? false,
            referenceCodeParticipating: values.referenceCodeParticipating ?? false,
            ruleFactVisible: values.ruleFactVisible ?? true,
        });
    }

    async function submitRelation() {
        const values = await relationForm.validateFields();
        createRelationMutation.mutate({ ...values, enabled: values.enabled ?? true });
    }

    async function submitMapping() {
        const values = await mappingForm.validateFields();
        createMappingMutation.mutate(values);
    }

    async function submitEvent() {
        const values = await eventForm.validateFields();
        createEventMutation.mutate({ ...values, enabled: values.enabled ?? true });
    }

    const objectColumns: TableColumnsType<ArchiveOntologyObjectTypeDto> = [
        { title: "编码", dataIndex: "typeCode", width: 180 },
        { title: "名称", dataIndex: "typeName" },
        {
            title: "来源",
            dataIndex: "builtin",
            width: 90,
            render: (builtin: boolean) => <Tag>{builtin ? "内置" : "本地"}</Tag>,
        },
        {
            title: "启用",
            dataIndex: "enabled",
            width: 90,
            render: (enabled: boolean) => (
                <Tag color={enabled ? "blue" : undefined}>{enabled ? "启用" : "停用"}</Tag>
            ),
        },
    ];

    const attributeColumns: TableColumnsType<ArchiveOntologyAttributeTypeDto> = [
        { title: "编码", dataIndex: "attributeCode", width: 180 },
        { title: "名称", dataIndex: "attributeName" },
        { title: "对象", dataIndex: "objectTypeId", width: 100 },
        { title: "类型", dataIndex: "dataType", width: 120 },
        { title: "元数据域", dataIndex: "metadataDomain", width: 130 },
        {
            title: "规则事实",
            dataIndex: "ruleFactVisible",
            width: 100,
            render: (visible: boolean) => (
                <Tag color={visible ? "blue" : undefined}>{visible ? "可见" : "隐藏"}</Tag>
            ),
        },
    ];

    const mappingColumns: TableColumnsType<ArchiveOntologyAttributeMappingDto> = [
        { title: "属性类型", dataIndex: "attributeTypeId", width: 100 },
        { title: "映射类型", dataIndex: "mappingKind", width: 150 },
        { title: "固定字段", dataIndex: "fixedFieldCode", width: 140 },
        { title: "分类", dataIndex: "categoryId", width: 90 },
        { title: "层级", dataIndex: "archiveLevel", width: 90 },
        { title: "动态字段", dataIndex: "dynamicFieldId", width: 100 },
        { title: "明细字段", dataIndex: "lineFieldId", width: 100 },
        { title: "文件组件字段", dataIndex: "componentFieldCode" },
        {
            title: "操作",
            width: 90,
            render: (_, row) => (
                <Popconfirm
                    title="确认删除属性映射？"
                    onConfirm={() => deleteMappingMutation.mutate(row.id)}
                >
                    <Button danger size="small" type="link">
                        删除
                    </Button>
                </Popconfirm>
            ),
        },
    ];

    const relationColumns: TableColumnsType<ArchiveOntologyRelationTypeDto> = [
        { title: "编码", dataIndex: "relationCode", width: 180 },
        { title: "名称", dataIndex: "relationName" },
        { title: "来源对象", dataIndex: "sourceObjectTypeId", width: 110 },
        { title: "目标对象", dataIndex: "targetObjectTypeId", width: 110 },
        { title: "方向", dataIndex: "relationDirection", width: 120 },
        { title: "基数", dataIndex: "cardinality", width: 130 },
    ];

    const eventColumns: TableColumnsType<ArchiveOntologyEventTypeDto> = [
        { title: "编码", dataIndex: "eventCode", width: 180 },
        { title: "名称", dataIndex: "eventName" },
        { title: "适用对象", dataIndex: "objectTypeId", width: 120 },
        {
            title: "启用",
            dataIndex: "enabled",
            width: 90,
            render: (enabled: boolean) => (
                <Tag color={enabled ? "blue" : undefined}>{enabled ? "启用" : "停用"}</Tag>
            ),
        },
    ];

    return (
        <section className="am-page">
            <div className="am-page__header">
                <Typography.Title level={1}>本体管理</Typography.Title>
            </div>
            <Card>
                <Tabs
                    items={[
                        {
                            key: "objects",
                            label: "对象类型",
                            children: (
                                <Table
                                    columns={objectColumns}
                                    dataSource={objectTypes}
                                    loading={objectTypesQuery.isLoading}
                                    pagination={false}
                                    rowKey="id"
                                    title={() => (
                                        <Space>
                                            <Button
                                                type="primary"
                                                onClick={() => setObjectModalOpen(true)}
                                            >
                                                新建对象
                                            </Button>
                                            <Button
                                                onClick={() => initializeObjectsMutation.mutate()}
                                            >
                                                初始化内置对象
                                            </Button>
                                        </Space>
                                    )}
                                />
                            ),
                        },
                        {
                            key: "attributes",
                            label: "属性类型",
                            children: (
                                <Table
                                    columns={attributeColumns}
                                    dataSource={attributesQuery.data?.items ?? []}
                                    loading={attributesQuery.isLoading}
                                    pagination={false}
                                    rowKey="id"
                                    title={() => (
                                        <Button
                                            type="primary"
                                            onClick={() => setAttributeModalOpen(true)}
                                        >
                                            新建属性
                                        </Button>
                                    )}
                                />
                            ),
                        },
                        {
                            key: "mappings",
                            label: "属性映射",
                            children: (
                                <Table
                                    columns={mappingColumns}
                                    dataSource={mappingsQuery.data?.items ?? []}
                                    loading={mappingsQuery.isLoading}
                                    pagination={false}
                                    rowKey="id"
                                    title={() => (
                                        <Button
                                            type="primary"
                                            onClick={() => setMappingModalOpen(true)}
                                        >
                                            新建映射
                                        </Button>
                                    )}
                                />
                            ),
                        },
                        {
                            key: "relations",
                            label: "关系类型",
                            children: (
                                <Table
                                    columns={relationColumns}
                                    dataSource={relationsQuery.data?.items ?? []}
                                    loading={relationsQuery.isLoading}
                                    pagination={false}
                                    rowKey="id"
                                    title={() => (
                                        <Button
                                            type="primary"
                                            onClick={() => setRelationModalOpen(true)}
                                        >
                                            新建关系
                                        </Button>
                                    )}
                                />
                            ),
                        },
                        {
                            key: "events",
                            label: "事件类型",
                            children: (
                                <Table
                                    columns={eventColumns}
                                    dataSource={eventsQuery.data?.items ?? []}
                                    loading={eventsQuery.isLoading}
                                    pagination={false}
                                    rowKey="id"
                                    title={() => (
                                        <Space>
                                            <Button
                                                type="primary"
                                                onClick={() => setEventModalOpen(true)}
                                            >
                                                新建事件
                                            </Button>
                                            <Button
                                                onClick={() => initializeEventsMutation.mutate()}
                                            >
                                                初始化内置事件
                                            </Button>
                                        </Space>
                                    )}
                                />
                            ),
                        },
                    ]}
                />
            </Card>
            <Modal
                open={objectModalOpen}
                title="新建对象类型"
                onCancel={() => setObjectModalOpen(false)}
                onOk={() => void submitObject()}
            >
                <Form form={objectForm} layout="vertical" initialValues={{ enabled: true }}>
                    <Form.Item label="编码" name="typeCode" rules={[{ required: true }]}>
                        <Input />
                    </Form.Item>
                    <Form.Item label="名称" name="typeName" rules={[{ required: true }]}>
                        <Input />
                    </Form.Item>
                    <Form.Item label="说明" name="description">
                        <Input.TextArea autoSize={{ minRows: 2, maxRows: 4 }} />
                    </Form.Item>
                    <Form.Item label="启用" name="enabled" valuePropName="checked">
                        <Switch />
                    </Form.Item>
                </Form>
            </Modal>
            <Modal
                open={attributeModalOpen}
                title="新建属性类型"
                onCancel={() => setAttributeModalOpen(false)}
                onOk={() => void submitAttribute()}
            >
                <Form
                    form={attributeForm}
                    layout="vertical"
                    initialValues={{
                        enabled: true,
                        cardinality: "SINGLE",
                        metadataDomain: "DESCRIPTION",
                        dataType: "TEXT",
                        ruleFactVisible: true,
                    }}
                >
                    <Form.Item label="编码" name="attributeCode" rules={[{ required: true }]}>
                        <Input />
                    </Form.Item>
                    <Form.Item label="名称" name="attributeName" rules={[{ required: true }]}>
                        <Input />
                    </Form.Item>
                    <Form.Item label="适用对象" name="objectTypeId" rules={[{ required: true }]}>
                        <Select options={objectOptions} />
                    </Form.Item>
                    <Form.Item label="数据类型" name="dataType">
                        <Select options={dataTypes.map((value) => ({ label: value, value }))} />
                    </Form.Item>
                    <Form.Item label="元数据域" name="metadataDomain">
                        <Select options={domains.map((value) => ({ label: value, value }))} />
                    </Form.Item>
                    <Form.Item label="基数" name="cardinality">
                        <Select
                            options={(
                                [
                                    "SINGLE",
                                    "MULTI",
                                    "REPEATED_ROW",
                                ] satisfies ArchiveOntologyCardinality[]
                            ).map((value) => ({ label: value, value }))}
                        />
                    </Form.Item>
                    <Form.Item label="规则事实可见" name="ruleFactVisible" valuePropName="checked">
                        <Switch />
                    </Form.Item>
                </Form>
            </Modal>
            <Modal
                open={relationModalOpen}
                title="新建关系类型"
                onCancel={() => setRelationModalOpen(false)}
                onOk={() => void submitRelation()}
            >
                <Form
                    form={relationForm}
                    layout="vertical"
                    initialValues={{
                        relationDirection: "ONE_WAY",
                        cardinality: "MANY_TO_MANY",
                        enabled: true,
                    }}
                >
                    <Form.Item label="编码" name="relationCode" rules={[{ required: true }]}>
                        <Input />
                    </Form.Item>
                    <Form.Item label="名称" name="relationName" rules={[{ required: true }]}>
                        <Input />
                    </Form.Item>
                    <Form.Item
                        label="来源对象"
                        name="sourceObjectTypeId"
                        rules={[{ required: true }]}
                    >
                        <Select options={objectOptions} />
                    </Form.Item>
                    <Form.Item
                        label="目标对象"
                        name="targetObjectTypeId"
                        rules={[{ required: true }]}
                    >
                        <Select options={objectOptions} />
                    </Form.Item>
                    <Form.Item label="方向" name="relationDirection">
                        <Select
                            options={(
                                [
                                    "ONE_WAY",
                                    "TWO_WAY",
                                    "HIERARCHICAL",
                                ] satisfies ArchiveOntologyRelationDirection[]
                            ).map((value) => ({ label: value, value }))}
                        />
                    </Form.Item>
                    <Form.Item label="基数" name="cardinality">
                        <Select
                            options={(
                                [
                                    "ONE_TO_ONE",
                                    "ONE_TO_MANY",
                                    "MANY_TO_ONE",
                                    "MANY_TO_MANY",
                                ] satisfies ArchiveOntologyRelationCardinality[]
                            ).map((value) => ({ label: value, value }))}
                        />
                    </Form.Item>
                </Form>
            </Modal>
            <Modal
                open={mappingModalOpen}
                title="新建属性映射"
                onCancel={() => setMappingModalOpen(false)}
                onOk={() => void submitMapping()}
            >
                <Form
                    form={mappingForm}
                    layout="vertical"
                    initialValues={{
                        mappingKind: "FIXED_FIELD",
                        archiveLevel: "ITEM",
                        fieldScope: "METADATA",
                    }}
                >
                    <Form.Item label="属性类型" name="attributeTypeId" rules={[{ required: true }]}>
                        <Select
                            options={(attributesQuery.data?.items ?? []).map((item) => ({
                                label: `${item.attributeName}（${item.attributeCode}）`,
                                value: item.id,
                            }))}
                        />
                    </Form.Item>
                    <Form.Item label="映射类型" name="mappingKind">
                        <Select
                            options={[
                                { label: "固定字段", value: "FIXED_FIELD" },
                                { label: "动态字段", value: "DYNAMIC_FIELD" },
                                { label: "明细字段", value: "LINE_FIELD" },
                                { label: "文件组件字段", value: "FILE_COMPONENT_FIELD" },
                                { label: "过程字段", value: "PROCESS_FIELD" },
                            ]}
                        />
                    </Form.Item>
                    <Form.Item label="固定字段编码" name="fixedFieldCode">
                        <Input />
                    </Form.Item>
                    <Form.Item label="分类 ID" name="categoryId">
                        <InputNumber min={1} style={{ width: "100%" }} />
                    </Form.Item>
                    <Form.Item label="对象层级" name="archiveLevel">
                        <Select
                            options={[
                                { label: "条目", value: "ITEM" },
                                { label: "案卷", value: "VOLUME" },
                            ]}
                        />
                    </Form.Item>
                    <Form.Item label="字段域" name="fieldScope">
                        <Select
                            options={[
                                { label: "元数据", value: "METADATA" },
                                { label: "实体保管", value: "PHYSICAL" },
                            ]}
                        />
                    </Form.Item>
                    <Form.Item label="动态字段 ID" name="dynamicFieldId">
                        <InputNumber min={1} style={{ width: "100%" }} />
                    </Form.Item>
                    <Form.Item label="明细表 ID" name="lineTableId">
                        <InputNumber min={1} style={{ width: "100%" }} />
                    </Form.Item>
                    <Form.Item label="明细字段 ID" name="lineFieldId">
                        <InputNumber min={1} style={{ width: "100%" }} />
                    </Form.Item>
                    <Form.Item label="文件组件字段编码" name="componentFieldCode">
                        <Input />
                    </Form.Item>
                    <Form.Item label="过程字段编码" name="processFieldCode">
                        <Input />
                    </Form.Item>
                </Form>
            </Modal>
            <Modal
                open={eventModalOpen}
                title="新建事件类型"
                onCancel={() => setEventModalOpen(false)}
                onOk={() => void submitEvent()}
            >
                <Form form={eventForm} layout="vertical" initialValues={{ enabled: true }}>
                    <Form.Item label="编码" name="eventCode" rules={[{ required: true }]}>
                        <Input />
                    </Form.Item>
                    <Form.Item label="名称" name="eventName" rules={[{ required: true }]}>
                        <Input />
                    </Form.Item>
                    <Form.Item label="适用对象" name="objectTypeId" rules={[{ required: true }]}>
                        <Select options={objectOptions} />
                    </Form.Item>
                </Form>
            </Modal>
        </section>
    );
}
