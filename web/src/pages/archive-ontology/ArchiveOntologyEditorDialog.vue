<script setup lang="ts">
import type { FormInstance } from "element-plus";
import { computed, ref } from "vue";

import type {
    ArchiveOntologyAttributeDataType,
    ArchiveOntologyAttributeTypeDto,
    ArchiveOntologyCardinality,
    ArchiveOntologyMetadataDomain,
    ArchiveOntologyObjectTypeDto,
    ArchiveOntologyRelationCardinality,
    ArchiveOntologyRelationDirection,
} from "@/shared/types/archive-ontology";

type EditorType = "object" | "attribute" | "mapping" | "relation" | "event";
const props = defineProps<{
    type: EditorType | null;
    form: Record<string, any>;
    objectTypes: ArchiveOntologyObjectTypeDto[];
    attributes: ArchiveOntologyAttributeTypeDto[];
    submitting: boolean;
}>();
const emit = defineEmits<{ close: []; submit: [] }>();
const formRef = ref<FormInstance>();
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
const cardinalities: ArchiveOntologyCardinality[] = ["SINGLE", "MULTI", "REPEATED_ROW"];
const relationDirections: ArchiveOntologyRelationDirection[] = [
    "ONE_WAY",
    "TWO_WAY",
    "HIERARCHICAL",
];
const relationCardinalities: ArchiveOntologyRelationCardinality[] = [
    "ONE_TO_ONE",
    "ONE_TO_MANY",
    "MANY_TO_ONE",
    "MANY_TO_MANY",
];
const objectOptions = computed(() =>
    props.objectTypes.map((item) => ({
        label: `${item.typeName}（${item.typeCode}）`,
        value: item.id,
    })),
);
const title = computed(
    () =>
        ({
            object: "新建对象类型",
            attribute: "新建属性类型",
            mapping: "新建属性映射",
            relation: "新建关系类型",
            event: "新建事件类型",
        })[props.type ?? "object"],
);

async function submit() {
    if (await formRef.value?.validate().catch(() => false)) emit("submit");
}
</script>

<template>
    <el-dialog
        :model-value="type !== null"
        :title="title"
        destroy-on-close
        @update:model-value="!$event && emit('close')"
    >
        <el-form ref="formRef" :model="form" label-position="top">
            <template v-if="type === 'object'">
                <el-form-item
                    label="编码"
                    prop="typeCode"
                    :rules="[{ required: true, message: '请输入编码' }]"
                    ><el-input v-model="form.typeCode"
                /></el-form-item>
                <el-form-item
                    label="名称"
                    prop="typeName"
                    :rules="[{ required: true, message: '请输入名称' }]"
                    ><el-input v-model="form.typeName"
                /></el-form-item>
                <el-form-item label="说明"
                    ><el-input v-model="form.description" type="textarea" :rows="3"
                /></el-form-item>
                <el-form-item label="启用"><el-switch v-model="form.enabled" /></el-form-item>
            </template>
            <template v-if="type === 'attribute'">
                <el-form-item
                    label="编码"
                    prop="attributeCode"
                    :rules="[{ required: true, message: '请输入编码' }]"
                    ><el-input v-model="form.attributeCode"
                /></el-form-item>
                <el-form-item
                    label="名称"
                    prop="attributeName"
                    :rules="[{ required: true, message: '请输入名称' }]"
                    ><el-input v-model="form.attributeName"
                /></el-form-item>
                <el-form-item
                    label="适用对象"
                    prop="objectTypeId"
                    :rules="[{ required: true, message: '请选择适用对象' }]"
                    ><el-select v-model="form.objectTypeId"
                        ><el-option
                            v-for="item in objectOptions"
                            :key="item.value"
                            v-bind="item" /></el-select
                ></el-form-item>
                <el-form-item label="数据类型"
                    ><el-select v-model="form.dataType"
                        ><el-option
                            v-for="value in dataTypes"
                            :key="value"
                            :label="value"
                            :value="value" /></el-select
                ></el-form-item>
                <el-form-item label="元数据域"
                    ><el-select v-model="form.metadataDomain"
                        ><el-option
                            v-for="value in domains"
                            :key="value"
                            :label="value"
                            :value="value" /></el-select
                ></el-form-item>
                <el-form-item label="基数"
                    ><el-select v-model="form.cardinality"
                        ><el-option
                            v-for="value in cardinalities"
                            :key="value"
                            :label="value"
                            :value="value" /></el-select
                ></el-form-item>
                <el-form-item label="规则事实可见"
                    ><el-switch v-model="form.ruleFactVisible"
                /></el-form-item>
            </template>
            <template v-if="type === 'relation'">
                <el-form-item
                    label="编码"
                    prop="relationCode"
                    :rules="[{ required: true, message: '请输入编码' }]"
                    ><el-input v-model="form.relationCode"
                /></el-form-item>
                <el-form-item
                    label="名称"
                    prop="relationName"
                    :rules="[{ required: true, message: '请输入名称' }]"
                    ><el-input v-model="form.relationName"
                /></el-form-item>
                <el-form-item
                    label="来源对象"
                    prop="sourceObjectTypeId"
                    :rules="[{ required: true, message: '请选择来源对象' }]"
                    ><el-select v-model="form.sourceObjectTypeId"
                        ><el-option
                            v-for="item in objectOptions"
                            :key="item.value"
                            v-bind="item" /></el-select
                ></el-form-item>
                <el-form-item
                    label="目标对象"
                    prop="targetObjectTypeId"
                    :rules="[{ required: true, message: '请选择目标对象' }]"
                    ><el-select v-model="form.targetObjectTypeId"
                        ><el-option
                            v-for="item in objectOptions"
                            :key="item.value"
                            v-bind="item" /></el-select
                ></el-form-item>
                <el-form-item label="方向"
                    ><el-select v-model="form.relationDirection"
                        ><el-option
                            v-for="value in relationDirections"
                            :key="value"
                            :label="value"
                            :value="value" /></el-select
                ></el-form-item>
                <el-form-item label="基数"
                    ><el-select v-model="form.cardinality"
                        ><el-option
                            v-for="value in relationCardinalities"
                            :key="value"
                            :label="value"
                            :value="value" /></el-select
                ></el-form-item>
            </template>
            <template v-if="type === 'mapping'">
                <el-form-item
                    label="属性类型"
                    prop="attributeTypeId"
                    :rules="[{ required: true, message: '请选择属性类型' }]"
                    ><el-select v-model="form.attributeTypeId"
                        ><el-option
                            v-for="item in attributes"
                            :key="item.id"
                            :label="`${item.attributeName}（${item.attributeCode}）`"
                            :value="item.id" /></el-select
                ></el-form-item>
                <el-form-item label="映射类型"
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
                            v-bind="item" /></el-select
                ></el-form-item>
                <el-form-item label="固定字段编码"
                    ><el-input v-model="form.fixedFieldCode"
                /></el-form-item>
                <el-form-item label="分类 ID"
                    ><el-input-number v-model="form.categoryId" :min="1"
                /></el-form-item>
                <el-form-item label="对象层级"
                    ><el-select v-model="form.archiveLevel"
                        ><el-option label="条目" value="ITEM" /><el-option
                            label="案卷"
                            value="VOLUME" /></el-select
                ></el-form-item>
                <el-form-item label="字段域"
                    ><el-select v-model="form.fieldScope"
                        ><el-option label="元数据" value="METADATA" /><el-option
                            label="实体保管"
                            value="PHYSICAL" /></el-select
                ></el-form-item>
                <el-form-item label="动态字段 ID"
                    ><el-input-number v-model="form.dynamicFieldId" :min="1"
                /></el-form-item>
                <el-form-item label="明细表 ID"
                    ><el-input-number v-model="form.lineTableId" :min="1"
                /></el-form-item>
                <el-form-item label="明细字段 ID"
                    ><el-input-number v-model="form.lineFieldId" :min="1"
                /></el-form-item>
                <el-form-item label="文件组件字段编码"
                    ><el-input v-model="form.componentFieldCode"
                /></el-form-item>
                <el-form-item label="过程字段编码"
                    ><el-input v-model="form.processFieldCode"
                /></el-form-item>
            </template>
            <template v-if="type === 'event'">
                <el-form-item
                    label="编码"
                    prop="eventCode"
                    :rules="[{ required: true, message: '请输入编码' }]"
                    ><el-input v-model="form.eventCode"
                /></el-form-item>
                <el-form-item
                    label="名称"
                    prop="eventName"
                    :rules="[{ required: true, message: '请输入名称' }]"
                    ><el-input v-model="form.eventName"
                /></el-form-item>
                <el-form-item
                    label="适用对象"
                    prop="objectTypeId"
                    :rules="[{ required: true, message: '请选择适用对象' }]"
                    ><el-select v-model="form.objectTypeId"
                        ><el-option
                            v-for="item in objectOptions"
                            :key="item.value"
                            v-bind="item" /></el-select
                ></el-form-item>
            </template>
        </el-form>
        <template #footer
            ><el-button @click="emit('close')">取消</el-button
            ><el-button type="primary" :loading="submitting" @click="submit"
                >确定</el-button
            ></template
        >
    </el-dialog>
</template>
