import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/vue";
import ElementPlus from "element-plus";
import { createPinia } from "pinia";
import { afterEach, beforeEach, describe, expect, it, vi } from "vite-plus/test";

import ApprovalWorkflowDesignerPage from "./ApprovalWorkflowDesignerPage.vue";

const approvalApiMocks = vi.hoisted(() => ({
    createApprovalWorkflowDefinition: vi.fn(),
    getApprovalWorkflowDefinition: vi.fn(),
    listApprovalWorkflowDefinitionVersions: vi.fn(),
    publishApprovalWorkflowDefinition: vi.fn(),
    updateApprovalWorkflowDefinition: vi.fn(),
}));
const authenticationApiMocks = vi.hoisted(() => ({ listAuthenticationUserOptions: vi.fn() }));
const routerMocks = vi.hoisted(() => ({ push: vi.fn(), replace: vi.fn() }));

vi.mock("@/shared/api/approval-workflow", () => approvalApiMocks);
vi.mock("@/shared/api/authentication", () => authenticationApiMocks);
vi.mock("vue-router", async (importOriginal) => ({
    ...(await importOriginal<typeof import("vue-router")>()),
    onBeforeRouteLeave: vi.fn(),
    useRoute: () => ({ params: { id: "new" } }),
    useRouter: () => routerMocks,
}));
vi.mock("./ApprovalWorkflowDesigner.vue", () => ({
    default: {
        name: "ApprovalWorkflowDesigner",
        props: ["graph"],
        template: '<div data-testid="designer-canvas" />',
    },
}));

beforeEach(() => {
    authenticationApiMocks.listAuthenticationUserOptions.mockResolvedValue({ items: [] });
    approvalApiMocks.createApprovalWorkflowDefinition.mockResolvedValue({ id: 9 });
    routerMocks.replace.mockResolvedValue(undefined);
});

afterEach(() => {
    cleanup();
    vi.clearAllMocks();
});

describe("审批流程设计器页面", () => {
    it("新建流程时载入默认画布并保存完整流程图", async () => {
        renderPage();

        expect(await screen.findByTestId("designer-canvas")).toBeInTheDocument();
        await fireEvent.update(
            screen.getByPlaceholderText("例如 archive_intake_flow"),
            "contract_flow",
        );
        const inputs = screen.getAllByRole("textbox");
        await fireEvent.update(inputs[1]!, "合同审批");
        await fireEvent.update(inputs[2]!, "contract");
        await fireEvent.click(screen.getByRole("button", { name: "保存草稿" }));

        await waitFor(() =>
            expect(approvalApiMocks.createApprovalWorkflowDefinition).toHaveBeenCalled(),
        );
        expect(approvalApiMocks.createApprovalWorkflowDefinition).toHaveBeenCalledWith(
            expect.objectContaining({
                definitionCode: "contract_flow",
                definitionName: "合同审批",
                businessType: "contract",
                graph: expect.objectContaining({
                    nodes: expect.arrayContaining([
                        expect.objectContaining({ nodeType: "START" }),
                        expect.objectContaining({ nodeType: "APPROVAL" }),
                        expect.objectContaining({ nodeType: "END" }),
                    ]),
                }),
            }),
        );
        expect(routerMocks.replace).toHaveBeenCalledWith({
            name: "approval-workflow-designer",
            params: { id: 9 },
        });
    });

    it("保存失败后保留用户输入以便重试", async () => {
        approvalApiMocks.createApprovalWorkflowDefinition.mockRejectedValueOnce(
            new Error("服务暂不可用"),
        );
        renderPage();

        await screen.findByTestId("designer-canvas");
        const codeInput = screen.getByPlaceholderText("例如 archive_intake_flow");
        const inputs = screen.getAllByRole("textbox");
        await fireEvent.update(codeInput, "contract_flow");
        await fireEvent.update(inputs[1]!, "合同审批");
        await fireEvent.update(inputs[2]!, "contract");
        await fireEvent.click(screen.getByRole("button", { name: "保存草稿" }));

        await waitFor(() =>
            expect(approvalApiMocks.createApprovalWorkflowDefinition).toHaveBeenCalledTimes(1),
        );
        expect(codeInput).toHaveValue("contract_flow");
        expect(inputs[1]).toHaveValue("合同审批");
    });
});

function renderPage() {
    return render(ApprovalWorkflowDesignerPage, {
        global: { plugins: [createPinia(), ElementPlus] },
    });
}
