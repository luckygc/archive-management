import { ElMessage, ElMessageBox } from "element-plus";
import { computed, onBeforeUnmount, onMounted, ref } from "vue";
import { onBeforeRouteLeave, useRoute, useRouter } from "vue-router";
import {
    createApprovalWorkflowDefinition,
    getApprovalWorkflowDefinition,
    listApprovalWorkflowDefinitionVersions,
    publishApprovalWorkflowDefinition,
    updateApprovalWorkflowDefinition,
} from "@/shared/api/approval-workflow";
import { listAuthenticationUserOptions } from "@/shared/api/authentication";
import type {
    ApprovalWorkflowDefinitionVersionDto,
    ApprovalWorkflowGraphDto,
} from "@/shared/types/approval-workflow";
import type { AuthenticationUserOptionDto } from "@/shared/types/authentication";
import { createDefaultApprovalGraph, validateApprovalGraph } from "./approval-workflow-graph";

export function useApprovalWorkflowDefinition() {
    const route = useRoute();
    const router = useRouter();
    const definitionId = ref<number>();
    const loading = ref(true);
    const saving = ref(false);
    const publishing = ref(false);
    const dirty = ref(false);
    const graph = ref<ApprovalWorkflowGraphDto>(createDefaultApprovalGraph());
    const form = ref({ definitionCode: "", definitionName: "", businessType: "" });
    const userOptions = ref<AuthenticationUserOptionDto[]>([]);
    const versions = ref<ApprovalWorkflowDefinitionVersionDto[]>([]);
    const versionsOpen = ref(false);
    const versionsLoading = ref(false);

    const issues = computed(() => validateApprovalGraph(graph.value));
    function markDirty() {
        dirty.value = true;
    }

    function updateGraph(value: ApprovalWorkflowGraphDto) {
        graph.value = value;
        markDirty();
    }

    function metadataError() {
        if (!form.value.definitionCode.trim()) return "请填写定义编码";
        if (!/^[a-z][a-z0-9_-]{0,99}$/.test(form.value.definitionCode.trim()))
            return "定义编码需以小写字母开头，只能包含小写字母、数字、下划线或连字符";
        if (!form.value.definitionName.trim()) return "请填写定义名称";
        if (!form.value.businessType.trim()) return "请填写业务类型";
        return undefined;
    }

    async function save(showSuccess = true) {
        const error = metadataError();
        if (error) {
            ElMessage.warning(error);
            return false;
        }
        saving.value = true;
        try {
            const payload = {
                definitionCode: form.value.definitionCode.trim(),
                definitionName: form.value.definitionName.trim(),
                businessType: form.value.businessType.trim(),
                graph: graph.value,
            };
            if (definitionId.value) {
                await updateApprovalWorkflowDefinition(definitionId.value, payload);
            } else {
                const created = await createApprovalWorkflowDefinition(payload);
                definitionId.value = created.id;
                await router.replace({
                    name: "approval-workflow-designer",
                    params: { id: created.id },
                });
            }
            dirty.value = false;
            if (showSuccess) ElMessage.success("流程草稿已保存");
            return true;
        } catch (errorValue) {
            ElMessage.error((errorValue as Error).message);
            return false;
        } finally {
            saving.value = false;
        }
    }

    async function publish() {
        const error = metadataError();
        if (error) return ElMessage.warning(error);
        if (issues.value.length > 0) return ElMessage.warning("请先处理画布中的校验问题");
        try {
            await ElMessageBox.confirm(
                "发布后，新发起的流程使用新版本；运行中的流程继续使用原版本。确认发布？",
                "发布流程",
                { type: "warning", confirmButtonText: "保存并发布" },
            );
        } catch {
            return;
        }
        publishing.value = true;
        try {
            if (!(await save(false)) || !definitionId.value) return;
            const version = await publishApprovalWorkflowDefinition(definitionId.value);
            ElMessage.success(`流程已发布为版本 ${version.versionNumber}`);
        } catch (errorValue) {
            ElMessage.error((errorValue as Error).message);
        } finally {
            publishing.value = false;
        }
    }

    async function openVersions() {
        if (!definitionId.value) return ElMessage.info("保存草稿后即可查看版本");
        versionsOpen.value = true;
        versionsLoading.value = true;
        try {
            versions.value = (
                await listApprovalWorkflowDefinitionVersions(definitionId.value, { limit: 100 })
            ).items;
        } catch (error) {
            ElMessage.error((error as Error).message);
        } finally {
            versionsLoading.value = false;
        }
    }

    function beforeUnload(event: BeforeUnloadEvent) {
        if (!dirty.value) return;
        event.preventDefault();
        event.returnValue = "";
    }

    onBeforeRouteLeave(async () => {
        if (!dirty.value) return true;
        try {
            await ElMessageBox.confirm("当前流程还有未保存的修改，确认离开？", "未保存修改", {
                type: "warning",
                confirmButtonText: "放弃修改",
                cancelButtonText: "继续编辑",
            });
            return true;
        } catch {
            return false;
        }
    });

    onMounted(async () => {
        window.addEventListener("beforeunload", beforeUnload);
        try {
            const rawId = route.params.id;
            const id = rawId === "new" ? undefined : Number(rawId);
            const requests: Promise<unknown>[] = [
                listAuthenticationUserOptions(1000).then((response) => {
                    userOptions.value = response.items;
                }),
            ];
            if (id && Number.isInteger(id) && id > 0) {
                requests.push(
                    getApprovalWorkflowDefinition(id).then((definition) => {
                        definitionId.value = definition.id;
                        form.value = {
                            definitionCode: definition.definitionCode,
                            definitionName: definition.definitionName,
                            businessType: definition.businessType,
                        };
                        graph.value = definition.graph;
                    }),
                );
            }
            await Promise.all(requests);
            dirty.value = false;
        } catch (error) {
            ElMessage.error((error as Error).message);
        } finally {
            loading.value = false;
        }
    });

    onBeforeUnmount(() => window.removeEventListener("beforeunload", beforeUnload));

    return {
        router,
        definitionId,
        loading,
        saving,
        publishing,
        dirty,
        graph,
        form,
        userOptions,
        versions,
        versionsOpen,
        versionsLoading,
        issues,
        markDirty,
        updateGraph,
        save,
        publish,
        openVersions,
    };
}
