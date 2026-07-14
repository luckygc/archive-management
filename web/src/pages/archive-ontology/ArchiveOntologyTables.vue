<script setup lang="ts">
import type {
    ArchiveOntologyAttributeMappingDto,
    ArchiveOntologyAttributeTypeDto,
    ArchiveOntologyEventTypeDto,
    ArchiveOntologyObjectTypeDto,
    ArchiveOntologyRelationTypeDto,
} from "@/shared/types/archive-ontology";

defineProps<{
    activeTab: string;
    loading: boolean;
    objectTypes: ArchiveOntologyObjectTypeDto[];
    attributes: ArchiveOntologyAttributeTypeDto[];
    mappings: ArchiveOntologyAttributeMappingDto[];
    relations: ArchiveOntologyRelationTypeDto[];
    events: ArchiveOntologyEventTypeDto[];
}>();
const emit = defineEmits<{
    "update:activeTab": [value: string];
    open: [type: "object" | "attribute" | "mapping" | "relation" | "event"];
    initialize: [kind: "objects" | "events"];
    deleteMapping: [value: unknown];
}>();
</script>

<template>
    <el-card shadow="never">
        <el-tabs
            :model-value="activeTab"
            @update:model-value="emit('update:activeTab', String($event))"
        >
            <el-tab-pane label="对象类型" name="objects">
                <div class="am-table-toolbar">
                    <el-button type="primary" @click="emit('open', 'object')">新建对象</el-button>
                    <el-button @click="emit('initialize', 'objects')">初始化内置对象</el-button>
                </div>
                <el-table v-loading="loading" :data="objectTypes" row-key="id">
                    <el-table-column prop="typeCode" label="编码" width="180" />
                    <el-table-column prop="typeName" label="名称" />
                    <el-table-column label="来源" width="90"
                        ><template #default="{ row }"
                            ><el-tag type="info">{{
                                row.builtin ? "内置" : "本地"
                            }}</el-tag></template
                        ></el-table-column
                    >
                    <el-table-column label="启用" width="90"
                        ><template #default="{ row }"
                            ><el-tag :type="row.enabled ? 'primary' : 'info'">{{
                                row.enabled ? "启用" : "停用"
                            }}</el-tag></template
                        ></el-table-column
                    >
                </el-table>
            </el-tab-pane>
            <el-tab-pane label="属性类型" name="attributes">
                <div class="am-table-toolbar">
                    <el-button type="primary" @click="emit('open', 'attribute')"
                        >新建属性</el-button
                    >
                </div>
                <el-table v-loading="loading" :data="attributes" row-key="id">
                    <el-table-column prop="attributeCode" label="编码" width="180" />
                    <el-table-column prop="attributeName" label="名称" />
                    <el-table-column prop="objectTypeId" label="对象" width="100" />
                    <el-table-column prop="dataType" label="类型" width="120" />
                    <el-table-column prop="metadataDomain" label="元数据域" width="130" />
                    <el-table-column label="规则事实" width="100"
                        ><template #default="{ row }"
                            ><el-tag :type="row.ruleFactVisible ? 'primary' : 'info'">{{
                                row.ruleFactVisible ? "可见" : "隐藏"
                            }}</el-tag></template
                        ></el-table-column
                    >
                </el-table>
            </el-tab-pane>
            <el-tab-pane label="属性映射" name="mappings">
                <div class="am-table-toolbar">
                    <el-button type="primary" @click="emit('open', 'mapping')">新建映射</el-button>
                </div>
                <el-table v-loading="loading" :data="mappings" row-key="id">
                    <el-table-column prop="attributeTypeId" label="属性类型" width="100" />
                    <el-table-column prop="mappingKind" label="映射类型" width="150" />
                    <el-table-column prop="fixedFieldCode" label="固定字段" width="140" />
                    <el-table-column prop="categoryId" label="分类" width="90" />
                    <el-table-column prop="archiveLevel" label="层级" width="90" />
                    <el-table-column prop="dynamicFieldId" label="动态字段" width="100" />
                    <el-table-column prop="lineFieldId" label="明细字段" width="100" />
                    <el-table-column prop="componentFieldCode" label="文件组件字段" />
                    <el-table-column label="操作" width="90"
                        ><template #default="{ row }"
                            ><el-button link type="danger" @click="emit('deleteMapping', row)"
                                >删除</el-button
                            ></template
                        ></el-table-column
                    >
                </el-table>
            </el-tab-pane>
            <el-tab-pane label="关系类型" name="relations">
                <div class="am-table-toolbar">
                    <el-button type="primary" @click="emit('open', 'relation')">新建关系</el-button>
                </div>
                <el-table v-loading="loading" :data="relations" row-key="id">
                    <el-table-column prop="relationCode" label="编码" width="180" />
                    <el-table-column prop="relationName" label="名称" />
                    <el-table-column prop="sourceObjectTypeId" label="来源对象" width="110" />
                    <el-table-column prop="targetObjectTypeId" label="目标对象" width="110" />
                    <el-table-column prop="relationDirection" label="方向" width="120" />
                    <el-table-column prop="cardinality" label="基数" width="130" />
                </el-table>
            </el-tab-pane>
            <el-tab-pane label="事件类型" name="events">
                <div class="am-table-toolbar">
                    <el-button type="primary" @click="emit('open', 'event')">新建事件</el-button>
                    <el-button @click="emit('initialize', 'events')">初始化内置事件</el-button>
                </div>
                <el-table v-loading="loading" :data="events" row-key="id">
                    <el-table-column prop="eventCode" label="编码" width="180" />
                    <el-table-column prop="eventName" label="名称" />
                    <el-table-column prop="objectTypeId" label="适用对象" width="120" />
                    <el-table-column label="启用" width="90"
                        ><template #default="{ row }"
                            ><el-tag :type="row.enabled ? 'primary' : 'info'">{{
                                row.enabled ? "启用" : "停用"
                            }}</el-tag></template
                        ></el-table-column
                    >
                </el-table>
            </el-tab-pane>
        </el-tabs>
    </el-card>
</template>

<style scoped>
.am-table-toolbar {
    display: flex;
    gap: 8px;
    margin-bottom: 12px;
}
</style>
