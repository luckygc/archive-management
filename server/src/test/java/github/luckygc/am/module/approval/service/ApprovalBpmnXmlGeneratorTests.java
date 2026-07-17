package github.luckygc.am.module.approval.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import github.luckygc.am.module.approval.ApprovalAction;
import github.luckygc.am.module.approval.ApprovalCandidateStrategy;
import github.luckygc.am.module.approval.ApprovalConditionOperator;
import github.luckygc.am.module.approval.ApprovalNodeType;
import github.luckygc.am.module.approval.service.ApprovalWorkflowTypes.ApprovalFlowCondition;
import github.luckygc.am.module.approval.service.ApprovalWorkflowTypes.ApprovalFlowEdge;
import github.luckygc.am.module.approval.service.ApprovalWorkflowTypes.ApprovalFlowNode;
import github.luckygc.am.module.approval.service.ApprovalWorkflowTypes.ApprovalWorkflowGraph;

@DisplayName("审批 BPMN XML 生成")
class ApprovalBpmnXmlGeneratorTests {

    private final ApprovalBpmnXmlGenerator generator = new ApprovalBpmnXmlGenerator();

    @Test
    @DisplayName("生成用户任务、排他网关、条件表达式和默认分支")
    void generateShouldKeepGraphAndControlledConditions() {
        ApprovalWorkflowGraph graph =
                new ApprovalWorkflowGraph(
                        List.of(
                                simpleNode("start", "开始", ApprovalNodeType.START),
                                approvalNode("department_review", "部门审核", 10L, 11L),
                                simpleNode(
                                        "amount_gateway",
                                        "金额判断",
                                        ApprovalNodeType.EXCLUSIVE_GATEWAY),
                                approvalNode("archive_review", "档案审核", 12L),
                                simpleNode("end", "结束", ApprovalNodeType.END)),
                        List.of(
                                edge("flow_start_review", "start", "department_review"),
                                edge("flow_review_gateway", "department_review", "amount_gateway"),
                                new ApprovalFlowEdge(
                                        "flow_gateway_archive",
                                        "amount_gateway",
                                        "archive_review",
                                        false,
                                        new ApprovalFlowCondition(
                                                "level",
                                                ApprovalConditionOperator.IN,
                                                List.of("重要", "核心"))),
                                new ApprovalFlowEdge(
                                        "flow_gateway_end", "amount_gateway", "end", true, null),
                                edge("flow_archive_end", "archive_review", "end")));

        String xml = generator.generate("approval_1", "合同审批", graph);

        assertThat(xml)
                .contains("<process id=\"approval_1\"")
                .contains("flowable:candidateUsers=\"10,11\"")
                .contains("<exclusiveGateway id=\"amount_gateway\"")
                .contains("default=\"flow_gateway_end\"")
                .contains("approvalConditionEvaluator.matches")
                .contains("'level', 'IN'")
                .contains("sourceRef=\"archive_review\" targetRef=\"end\"");
    }

    @Test
    @DisplayName("条件值编码后不会注入 XML 或表达式")
    void conditionValueShouldBeEncoded() {
        ApprovalWorkflowGraph graph =
                new ApprovalWorkflowGraph(
                        List.of(
                                simpleNode("start", "开始", ApprovalNodeType.START),
                                simpleNode("gateway", "判断", ApprovalNodeType.EXCLUSIVE_GATEWAY),
                                approvalNode("review", "审核", 10L),
                                simpleNode("end", "结束", ApprovalNodeType.END)),
                        List.of(
                                edge("flow_start_gateway", "start", "gateway"),
                                new ApprovalFlowEdge(
                                        "flow_gateway_review",
                                        "gateway",
                                        "review",
                                        false,
                                        new ApprovalFlowCondition(
                                                "level",
                                                ApprovalConditionOperator.EQUALS,
                                                List.of("x') or true or ('"))),
                                new ApprovalFlowEdge(
                                        "flow_gateway_end", "gateway", "end", true, null),
                                edge("flow_review_end", "review", "end")));

        String xml = generator.generate("approval_1", "合同审批", graph);

        assertThat(xml).doesNotContain("x') or true");
    }

    private ApprovalFlowNode approvalNode(String code, String name, Long... userIds) {
        return new ApprovalFlowNode(
                code,
                name,
                ApprovalNodeType.APPROVAL,
                200,
                200,
                ApprovalCandidateStrategy.SPECIFIED_USERS,
                List.of(userIds),
                List.of(ApprovalAction.APPROVE, ApprovalAction.REJECT));
    }

    private ApprovalFlowNode simpleNode(String code, String name, ApprovalNodeType type) {
        return new ApprovalFlowNode(code, name, type, 100, 100, null, List.of(), List.of());
    }

    private ApprovalFlowEdge edge(String code, String source, String target) {
        return new ApprovalFlowEdge(code, source, target, false, null);
    }
}
