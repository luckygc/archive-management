package github.luckygc.am.module.approval.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import github.luckygc.am.common.exception.BadRequestException;
import github.luckygc.am.module.approval.ApprovalAction;
import github.luckygc.am.module.approval.ApprovalCandidateStrategy;
import github.luckygc.am.module.approval.ApprovalConditionOperator;
import github.luckygc.am.module.approval.ApprovalNodeType;
import github.luckygc.am.module.approval.service.ApprovalWorkflowTypes.ApprovalFlowCondition;
import github.luckygc.am.module.approval.service.ApprovalWorkflowTypes.ApprovalFlowEdge;
import github.luckygc.am.module.approval.service.ApprovalWorkflowTypes.ApprovalFlowNode;
import github.luckygc.am.module.approval.service.ApprovalWorkflowTypes.ApprovalWorkflowGraph;

@DisplayName("审批流程图校验")
class ApprovalWorkflowGraphValidatorTests {

    private final ApprovalWorkflowGraphValidator validator = new ApprovalWorkflowGraphValidator();

    @Test
    @DisplayName("合法条件分支流程通过发布校验")
    void validConditionalGraphShouldPass() {
        ApprovalWorkflowGraph graph = validator.validateForPublishing(branchGraph());

        assertThat(graph.nodes()).hasSize(6);
        assertThat(graph.edges()).hasSize(6);
    }

    @Test
    @DisplayName("断开的节点被拒绝")
    void disconnectedNodeShouldBeRejected() {
        ApprovalWorkflowGraph graph =
                new ApprovalWorkflowGraph(
                        List.of(
                                simpleNode("start", "开始", ApprovalNodeType.START),
                                approvalNode("review", "审核"),
                                approvalNode("isolated", "孤立审核"),
                                simpleNode("end", "结束", ApprovalNodeType.END)),
                        List.of(
                                edge("flow_start_review", "start", "review"),
                                edge("flow_review_end", "review", "end")));

        assertThatThrownBy(() -> validator.validateForPublishing(graph))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("审批流程图不合法");
    }

    @Test
    @DisplayName("流程环路被拒绝")
    void cycleShouldBeRejected() {
        ApprovalWorkflowGraph graph =
                new ApprovalWorkflowGraph(
                        List.of(
                                simpleNode("start", "开始", ApprovalNodeType.START),
                                approvalNode("review_a", "审核 A"),
                                simpleNode("gateway", "判断", ApprovalNodeType.EXCLUSIVE_GATEWAY),
                                approvalNode("review_b", "审核 B"),
                                simpleNode("end", "结束", ApprovalNodeType.END)),
                        List.of(
                                edge("flow_start_a", "start", "review_a"),
                                edge("flow_a_gateway", "review_a", "gateway"),
                                conditionalEdge("flow_gateway_b", "gateway", "review_b", false),
                                new ApprovalFlowEdge(
                                        "flow_gateway_end", "gateway", "end", true, null),
                                edge("flow_b_a", "review_b", "review_a")));

        assertThatThrownBy(() -> validator.validateForPublishing(graph))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("重复节点编码被拒绝")
    void duplicateNodeCodeShouldBeRejected() {
        ApprovalWorkflowGraph graph =
                new ApprovalWorkflowGraph(
                        List.of(
                                simpleNode("start", "开始", ApprovalNodeType.START),
                                approvalNode("start", "重复"),
                                simpleNode("end", "结束", ApprovalNodeType.END)),
                        List.of());

        assertThatThrownBy(() -> validator.validateDraft(graph))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("缺少默认出线的条件分支被拒绝")
    void gatewayWithoutDefaultShouldBeRejected() {
        ApprovalWorkflowGraph source = branchGraph();
        List<ApprovalFlowEdge> edges =
                source.edges().stream()
                        .map(
                                edge ->
                                        edge.defaultFlow()
                                                ? conditionalEdge(
                                                        edge.edgeCode(),
                                                        edge.sourceNodeCode(),
                                                        edge.targetNodeCode(),
                                                        false)
                                                : edge)
                        .toList();

        assertThatThrownBy(
                        () ->
                                validator.validateForPublishing(
                                        new ApprovalWorkflowGraph(source.nodes(), edges)))
                .isInstanceOf(BadRequestException.class);
    }

    private ApprovalWorkflowGraph branchGraph() {
        return new ApprovalWorkflowGraph(
                List.of(
                        simpleNode("start", "开始", ApprovalNodeType.START),
                        approvalNode("submit_review", "初审"),
                        simpleNode("amount_gateway", "金额判断", ApprovalNodeType.EXCLUSIVE_GATEWAY),
                        approvalNode("manager_review", "负责人审批"),
                        approvalNode("archive_review", "档案审批"),
                        simpleNode("end", "结束", ApprovalNodeType.END)),
                List.of(
                        edge("flow_start_submit", "start", "submit_review"),
                        edge("flow_submit_gateway", "submit_review", "amount_gateway"),
                        conditionalEdge(
                                "flow_gateway_manager", "amount_gateway", "manager_review", false),
                        new ApprovalFlowEdge(
                                "flow_gateway_archive",
                                "amount_gateway",
                                "archive_review",
                                true,
                                null),
                        edge("flow_manager_end", "manager_review", "end"),
                        edge("flow_archive_end", "archive_review", "end")));
    }

    private ApprovalFlowNode approvalNode(String code, String name) {
        return new ApprovalFlowNode(
                code,
                name,
                ApprovalNodeType.APPROVAL,
                200,
                200,
                ApprovalCandidateStrategy.SPECIFIED_USERS,
                List.of(20L),
                List.of(ApprovalAction.APPROVE, ApprovalAction.REJECT));
    }

    private ApprovalFlowNode simpleNode(String code, String name, ApprovalNodeType type) {
        return new ApprovalFlowNode(code, name, type, 100, 100, null, List.of(), List.of());
    }

    private ApprovalFlowEdge edge(String code, String source, String target) {
        return new ApprovalFlowEdge(code, source, target, false, null);
    }

    private ApprovalFlowEdge conditionalEdge(
            String code, String source, String target, boolean defaultFlow) {
        return new ApprovalFlowEdge(
                code,
                source,
                target,
                defaultFlow,
                new ApprovalFlowCondition(
                        "amount", ApprovalConditionOperator.EQUALS, List.of("100")));
    }
}
