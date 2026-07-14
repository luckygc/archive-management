<script setup lang="ts">
import { ElMessage, ElMessageBox, type FormInstance } from "element-plus";
import { computed, onMounted, ref } from "vue";

import {
    createArchiveOntologyAttributeMapping,
    createArchiveOntologyAttributeType,
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
} from "@/shared/api/archive-ontology";
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
} from "@/shared/types/archive-ontology";

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
const objectTypes = ref<ArchiveOntologyObjectTypeDto[]>([]);
const attributes = ref<ArchiveOntologyAttributeTypeDto[]>([]);
const mappings = ref<ArchiveOntologyAttributeMappingDto[]>([]);
const relations = ref<ArchiveOntologyRelationTypeDto[]>([]);
const events = ref<ArchiveOntologyEventTypeDto[]>([]);
const loading = ref(false);
const activeTab = ref("objects");
const modal = ref<"object" | "attribute" | "mapping" | "relation" | "event" | null>(null);
const submitting = ref(false);
const formRef = ref<FormInstance>();
const form = ref<Record<string, any>>({});
const objectOptions = computed(() =>
    objectTypes.value.map((item) => ({
        label: `${item.typeName}（${item.typeCode}）`,
        value: item.id,
    })),
);

async function loadAll() {
    loading.value = true;
    try {
        const [objectResult, attributeResult, mappingResult, relationResult, eventResult] =
            await Promise.all([
                listArchiveOntologyObjectTypes(),
                listArchiveOntologyAttributeTypes(),
                listArchiveOntologyAttributeMappings(),
                listArchiveOntologyRelationTypes(),
                listArchiveOntologyEventTypes(),
            ]);
        objectTypes.value = objectResult.items;
        attributes.value = attributeResult.items;
        mappings.value = mappingResult.items;
        relations.value = relationResult.items;
        events.value = eventResult.items;
    } catch (error) {
        ElMessage.error((error as Error).message);
    } finally {
        loading.value = false;
    }
}
onMounted(() => void loadAll());

function openModal(type: NonNullable<typeof modal.value>) {
    modal.value = type;
    if (type === "object")
        form.value = { typeCode: "", typeName: "", description: "", enabled: true };
    if (type === "attribute")
        form.value = {
            attributeCode: "",
            attributeName: "",
            objectTypeId: undefined,
            dataType: "TEXT",
            metadataDomain: "DESCRIPTION",
            cardinality: "SINGLE",
            ruleFactVisible: true,
            enabled: true,
        };
    if (type === "mapping")
        form.value = {
            attributeTypeId: undefined,
            mappingKind: "FIXED_FIELD",
            archiveLevel: "ITEM",
            fieldScope: "METADATA",
        };
    if (type === "relation")
        form.value = {
            relationCode: "",
            relationName: "",
            sourceObjectTypeId: undefined,
            targetObjectTypeId: undefined,
            relationDirection: "ONE_WAY",
            cardinality: "MANY_TO_MANY",
            enabled: true,
        };
    if (type === "event")
        form.value = { eventCode: "", eventName: "", objectTypeId: undefined, enabled: true };
}

async function submit() {
    if (!(await formRef.value?.validate().catch(() => false)) || !modal.value) return;
    submitting.value = true;
    try {
        if (modal.value === "object")
            await createArchiveOntologyObjectType({
                typeCode: form.value.typeCode,
                typeName: form.value.typeName,
                description: form.value.description || undefined,
                enabled: form.value.enabled ?? true,
            });
        if (modal.value === "attribute")
            await createArchiveOntologyAttributeType({
                attributeCode: form.value.attributeCode,
                attributeName: form.value.attributeName,
                objectTypeId: form.value.objectTypeId,
                dataType: form.value.dataType,
                metadataDomain: form.value.metadataDomain,
                cardinality: form.value.cardinality,
                exactSearchable: form.value.exactSearchable ?? false,
                sortable: form.value.sortable ?? false,
                descriptionParticipating: form.value.descriptionParticipating ?? false,
                referenceCodeParticipating: form.value.referenceCodeParticipating ?? false,
                ruleFactVisible: form.value.ruleFactVisible ?? true,
                enabled: form.value.enabled ?? true,
            });
        if (modal.value === "mapping")
            await createArchiveOntologyAttributeMapping(form.value as never);
        if (modal.value === "relation")
            await createArchiveOntologyRelationType({
                ...form.value,
                enabled: form.value.enabled ?? true,
            } as never);
        if (modal.value === "event")
            await createArchiveOntologyEventType({
                ...form.value,
                enabled: form.value.enabled ?? true,
            } as never);
        ElMessage.success(
            {
                object: "对象类型已创建",
                attribute: "属性类型已创建",
                mapping: "属性映射已创建",
                relation: "关系类型已创建",
                event: "事件类型已创建",
            }[modal.value],
        );
        modal.value = null;
        await loadAll();
    } catch (error) {
        ElMessage.error((error as Error).message);
    } finally {
        submitting.value = false;
    }
}

async function initialize(kind: "objects" | "events") {
    try {
        await (kind === "objects"
            ? initializeArchiveOntologyObjectTypes()
            : initializeArchiveOntologyEventTypes());
        ElMessage.success(kind === "objects" ? "内置对象类型已初始化" : "内置事件类型已初始化");
        await loadAll();
    } catch (error) {
        ElMessage.error((error as Error).message);
    }
}

async function deleteMapping(value: unknown) {
    const row = value as ArchiveOntologyAttributeMappingDto;
    try {
        await ElMessageBox.confirm("确认删除属性映射？", "提示", { type: "warning" });
        await deleteArchiveOntologyAttributeMapping(row.id);
        ElMessage.success("属性映射已删除");
        await loadAll();
    } catch (error) {
        if (error !== "cancel" && error !== "close") ElMessage.error((error as Error).message);
    }
}
</script>

<template>
    <section class="am-page">
        <div class="am-page__header"><h1>本体管理</h1></div>
        <el-card shadow="never">
            <el-tabs v-model="activeTab">
                <el-tab-pane label="对象类型" name="objects">
                    <div class="am-table-toolbar">
                        <el-button type="primary" @click="openModal('object')">新建对象</el-button
                        ><el-button @click="initialize('objects')">初始化内置对象</el-button>
                    </div>
                    <el-table v-loading="loading" :data="objectTypes" row-key="id"
                        ><el-table-column
                            prop="typeCode"
                            label="编码"
                            width="180"
                        /><el-table-column prop="typeName" label="名称" /><el-table-column
                            label="来源"
                            width="90"
                            ><template #default="{ row }"
                                ><el-tag type="info">{{
                                    row.builtin ? "内置" : "本地"
                                }}</el-tag></template
                            ></el-table-column
                        ><el-table-column label="启用" width="90"
                            ><template #default="{ row }"
                                ><el-tag :type="row.enabled ? 'primary' : 'info'">{{
                                    row.enabled ? "启用" : "停用"
                                }}</el-tag></template
                            ></el-table-column
                        ></el-table
                    >
                </el-tab-pane>
                <el-tab-pane label="属性类型" name="attributes">
                    <div class="am-table-toolbar">
                        <el-button type="primary" @click="openModal('attribute')"
                            >新建属性</el-button
                        >
                    </div>
                    <el-table v-loading="loading" :data="attributes" row-key="id"
                        ><el-table-column
                            prop="attributeCode"
                            label="编码"
                            width="180"
                        /><el-table-column prop="attributeName" label="名称" /><el-table-column
                            prop="objectTypeId"
                            label="对象"
                            width="100"
                        /><el-table-column
                            prop="dataType"
                            label="类型"
                            width="120"
                        /><el-table-column
                            prop="metadataDomain"
                            label="元数据域"
                            width="130"
                        /><el-table-column label="规则事实" width="100"
                            ><template #default="{ row }"
                                ><el-tag :type="row.ruleFactVisible ? 'primary' : 'info'">{{
                                    row.ruleFactVisible ? "可见" : "隐藏"
                                }}</el-tag></template
                            ></el-table-column
                        ></el-table
                    >
                </el-tab-pane>
                <el-tab-pane label="属性映射" name="mappings">
                    <div class="am-table-toolbar">
                        <el-button type="primary" @click="openModal('mapping')">新建映射</el-button>
                    </div>
                    <el-table v-loading="loading" :data="mappings" row-key="id"
                        ><el-table-column
                            prop="attributeTypeId"
                            label="属性类型"
                            width="100"
                        /><el-table-column
                            prop="mappingKind"
                            label="映射类型"
                            width="150"
                        /><el-table-column
                            prop="fixedFieldCode"
                            label="固定字段"
                            width="140"
                        /><el-table-column
                            prop="categoryId"
                            label="分类"
                            width="90"
                        /><el-table-column
                            prop="archiveLevel"
                            label="层级"
                            width="90"
                        /><el-table-column
                            prop="dynamicFieldId"
                            label="动态字段"
                            width="100"
                        /><el-table-column
                            prop="lineFieldId"
                            label="明细字段"
                            width="100"
                        /><el-table-column
                            prop="componentFieldCode"
                            label="文件组件字段"
                        /><el-table-column label="操作" width="90"
                            ><template #default="{ row }"
                                ><el-button link type="danger" @click="deleteMapping(row)"
                                    >删除</el-button
                                ></template
                            ></el-table-column
                        ></el-table
                    >
                </el-tab-pane>
                <el-tab-pane label="关系类型" name="relations"
                    ><div class="am-table-toolbar">
                        <el-button type="primary" @click="openModal('relation')"
                            >新建关系</el-button
                        >
                    </div>
                    <el-table v-loading="loading" :data="relations" row-key="id"
                        ><el-table-column
                            prop="relationCode"
                            label="编码"
                            width="180" /><el-table-column
                            prop="relationName"
                            label="名称" /><el-table-column
                            prop="sourceObjectTypeId"
                            label="来源对象"
                            width="110" /><el-table-column
                            prop="targetObjectTypeId"
                            label="目标对象"
                            width="110" /><el-table-column
                            prop="relationDirection"
                            label="方向"
                            width="120" /><el-table-column
                            prop="cardinality"
                            label="基数"
                            width="130" /></el-table
                ></el-tab-pane>
                <el-tab-pane label="事件类型" name="events"
                    ><div class="am-table-toolbar">
                        <el-button type="primary" @click="openModal('event')">新建事件</el-button
                        ><el-button @click="initialize('events')">初始化内置事件</el-button>
                    </div>
                    <el-table v-loading="loading" :data="events" row-key="id"
                        ><el-table-column
                            prop="eventCode"
                            label="编码"
                            width="180"
                        /><el-table-column prop="eventName" label="名称" /><el-table-column
                            prop="objectTypeId"
                            label="适用对象"
                            width="120"
                        /><el-table-column label="启用" width="90"
                            ><template #default="{ row }"
                                ><el-tag :type="row.enabled ? 'primary' : 'info'">{{
                                    row.enabled ? "启用" : "停用"
                                }}</el-tag></template
                            ></el-table-column
                        ></el-table
                    ></el-tab-pane
                >
            </el-tabs>
        </el-card>

        <el-dialog
            :model-value="modal !== null"
            @update:model-value="!$event && (modal = null)"
            :title="
                (
                    {
                        object: '新建对象类型',
                        attribute: '新建属性类型',
                        mapping: '新建属性映射',
                        relation: '新建关系类型',
                        event: '新建事件类型',
                    } as Record<string, string>
                )[modal || '']
            "
            destroy-on-close
        >
            <el-form ref="formRef" :model="form" label-position="top">
                <template v-if="modal === 'object'"
                    ><el-form-item
                        label="编码"
                        prop="typeCode"
                        :rules="[{ required: true, message: '请输入编码' }]"
                        ><el-input v-model="form.typeCode" /></el-form-item
                    ><el-form-item
                        label="名称"
                        prop="typeName"
                        :rules="[{ required: true, message: '请输入名称' }]"
                        ><el-input v-model="form.typeName" /></el-form-item
                    ><el-form-item label="说明"
                        ><el-input
                            v-model="form.description"
                            type="textarea"
                            :rows="3" /></el-form-item
                    ><el-form-item label="启用"><el-switch v-model="form.enabled" /></el-form-item
                ></template>
                <template v-if="modal === 'attribute'"
                    ><el-form-item
                        label="编码"
                        prop="attributeCode"
                        :rules="[{ required: true, message: '请输入编码' }]"
                        ><el-input v-model="form.attributeCode" /></el-form-item
                    ><el-form-item
                        label="名称"
                        prop="attributeName"
                        :rules="[{ required: true, message: '请输入名称' }]"
                        ><el-input v-model="form.attributeName" /></el-form-item
                    ><el-form-item
                        label="适用对象"
                        prop="objectTypeId"
                        :rules="[{ required: true, message: '请选择适用对象' }]"
                        ><el-select v-model="form.objectTypeId"
                            ><el-option
                                v-for="item in objectOptions"
                                :key="item.value"
                                v-bind="item" /></el-select></el-form-item
                    ><el-form-item label="数据类型"
                        ><el-select v-model="form.dataType"
                            ><el-option
                                v-for="value in dataTypes"
                                :key="value"
                                :label="value"
                                :value="value" /></el-select></el-form-item
                    ><el-form-item label="元数据域"
                        ><el-select v-model="form.metadataDomain"
                            ><el-option
                                v-for="value in domains"
                                :key="value"
                                :label="value"
                                :value="value" /></el-select></el-form-item
                    ><el-form-item label="基数"
                        ><el-select v-model="form.cardinality"
                            ><el-option
                                v-for="value in [
                                    'SINGLE',
                                    'MULTI',
                                    'REPEATED_ROW',
                                ] as ArchiveOntologyCardinality[]"
                                :key="value"
                                :label="value"
                                :value="value" /></el-select></el-form-item
                    ><el-form-item label="规则事实可见"
                        ><el-switch v-model="form.ruleFactVisible" /></el-form-item
                ></template>
                <template v-if="modal === 'relation'"
                    ><el-form-item
                        label="编码"
                        prop="relationCode"
                        :rules="[{ required: true, message: '请输入编码' }]"
                        ><el-input v-model="form.relationCode" /></el-form-item
                    ><el-form-item
                        label="名称"
                        prop="relationName"
                        :rules="[{ required: true, message: '请输入名称' }]"
                        ><el-input v-model="form.relationName" /></el-form-item
                    ><el-form-item
                        label="来源对象"
                        prop="sourceObjectTypeId"
                        :rules="[{ required: true, message: '请选择来源对象' }]"
                        ><el-select v-model="form.sourceObjectTypeId"
                            ><el-option
                                v-for="item in objectOptions"
                                :key="item.value"
                                v-bind="item" /></el-select></el-form-item
                    ><el-form-item
                        label="目标对象"
                        prop="targetObjectTypeId"
                        :rules="[{ required: true, message: '请选择目标对象' }]"
                        ><el-select v-model="form.targetObjectTypeId"
                            ><el-option
                                v-for="item in objectOptions"
                                :key="item.value"
                                v-bind="item" /></el-select></el-form-item
                    ><el-form-item label="方向"
                        ><el-select v-model="form.relationDirection"
                            ><el-option
                                v-for="value in [
                                    'ONE_WAY',
                                    'TWO_WAY',
                                    'HIERARCHICAL',
                                ] as ArchiveOntologyRelationDirection[]"
                                :key="value"
                                :label="value"
                                :value="value" /></el-select></el-form-item
                    ><el-form-item label="基数"
                        ><el-select v-model="form.cardinality"
                            ><el-option
                                v-for="value in [
                                    'ONE_TO_ONE',
                                    'ONE_TO_MANY',
                                    'MANY_TO_ONE',
                                    'MANY_TO_MANY',
                                ] as ArchiveOntologyRelationCardinality[]"
                                :key="value"
                                :label="value"
                                :value="value" /></el-select></el-form-item
                ></template>
                <template v-if="modal === 'mapping'"
                    ><el-form-item
                        label="属性类型"
                        prop="attributeTypeId"
                        :rules="[{ required: true, message: '请选择属性类型' }]"
                        ><el-select v-model="form.attributeTypeId"
                            ><el-option
                                v-for="item in attributes"
                                :key="item.id"
                                :label="`${item.attributeName}（${item.attributeCode}）`"
                                :value="item.id" /></el-select></el-form-item
                    ><el-form-item label="映射类型"
                        ><el-select v-model="form.mappingKind"
                            ><el-option
                                v-for="item in [
                                    { label: '固定字段', value: 'FIXED_FIELD' },
                                    { label: '动态字段', value: 'DYNAMIC_FIELD' },
                                    { label: '明细字段', value: 'LINE_FIELD' },
                                    { label: '文件组件字段', value: 'FILE_COMPONENT_FIELD' },
                                    { label: '过程字段', value: 'PROCESS_FIELD' },
                                ]"
                                :key="item.value"
                                v-bind="item" /></el-select></el-form-item
                    ><el-form-item label="固定字段编码"
                        ><el-input v-model="form.fixedFieldCode" /></el-form-item
                    ><el-form-item label="分类 ID"
                        ><el-input-number v-model="form.categoryId" :min="1" /></el-form-item
                    ><el-form-item label="对象层级"
                        ><el-select v-model="form.archiveLevel"
                            ><el-option label="条目" value="ITEM" /><el-option
                                label="案卷"
                                value="VOLUME" /></el-select></el-form-item
                    ><el-form-item label="字段域"
                        ><el-select v-model="form.fieldScope"
                            ><el-option label="元数据" value="METADATA" /><el-option
                                label="实体保管"
                                value="PHYSICAL" /></el-select></el-form-item
                    ><el-form-item label="动态字段 ID"
                        ><el-input-number v-model="form.dynamicFieldId" :min="1" /></el-form-item
                    ><el-form-item label="明细表 ID"
                        ><el-input-number v-model="form.lineTableId" :min="1" /></el-form-item
                    ><el-form-item label="明细字段 ID"
                        ><el-input-number v-model="form.lineFieldId" :min="1" /></el-form-item
                    ><el-form-item label="文件组件字段编码"
                        ><el-input v-model="form.componentFieldCode" /></el-form-item
                    ><el-form-item label="过程字段编码"
                        ><el-input v-model="form.processFieldCode" /></el-form-item
                ></template>
                <template v-if="modal === 'event'"
                    ><el-form-item
                        label="编码"
                        prop="eventCode"
                        :rules="[{ required: true, message: '请输入编码' }]"
                        ><el-input v-model="form.eventCode" /></el-form-item
                    ><el-form-item
                        label="名称"
                        prop="eventName"
                        :rules="[{ required: true, message: '请输入名称' }]"
                        ><el-input v-model="form.eventName" /></el-form-item
                    ><el-form-item
                        label="适用对象"
                        prop="objectTypeId"
                        :rules="[{ required: true, message: '请选择适用对象' }]"
                        ><el-select v-model="form.objectTypeId"
                            ><el-option
                                v-for="item in objectOptions"
                                :key="item.value"
                                v-bind="item" /></el-select></el-form-item
                ></template>
            </el-form>
            <template #footer
                ><el-button @click="modal = null">取消</el-button
                ><el-button type="primary" :loading="submitting" @click="submit"
                    >确定</el-button
                ></template
            >
        </el-dialog>
    </section>
</template>

<style scoped>
.am-table-toolbar {
    display: flex;
    gap: 8px;
    margin-bottom: 12px;
}
</style>
