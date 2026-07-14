<script setup lang="ts">
import { ElMessage, ElMessageBox } from "element-plus";
import { onMounted, ref } from "vue";

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
    ArchiveOntologyAttributeMappingDto,
    ArchiveOntologyAttributeTypeDto,
    ArchiveOntologyEventTypeDto,
    ArchiveOntologyObjectTypeDto,
    ArchiveOntologyRelationTypeDto,
} from "@/shared/types/archive-ontology";
import ArchiveOntologyEditorDialog from "./ArchiveOntologyEditorDialog.vue";
import ArchiveOntologyTables from "./ArchiveOntologyTables.vue";

const objectTypes = ref<ArchiveOntologyObjectTypeDto[]>([]);
const attributes = ref<ArchiveOntologyAttributeTypeDto[]>([]);
const mappings = ref<ArchiveOntologyAttributeMappingDto[]>([]);
const relations = ref<ArchiveOntologyRelationTypeDto[]>([]);
const events = ref<ArchiveOntologyEventTypeDto[]>([]);
const loading = ref(false);
const activeTab = ref("objects");
const modal = ref<"object" | "attribute" | "mapping" | "relation" | "event" | null>(null);
const submitting = ref(false);
const form = ref<Record<string, any>>({});

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
    if (!modal.value) return;
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
        <ArchiveOntologyTables
            v-model:active-tab="activeTab"
            :loading="loading"
            :object-types="objectTypes"
            :attributes="attributes"
            :mappings="mappings"
            :relations="relations"
            :events="events"
            @open="openModal"
            @initialize="initialize"
            @delete-mapping="deleteMapping"
        />
        <ArchiveOntologyEditorDialog
            :type="modal"
            :form="form"
            :object-types="objectTypes"
            :attributes="attributes"
            :submitting="submitting"
            @close="modal = null"
            @submit="submit"
        />
    </section>
</template>
