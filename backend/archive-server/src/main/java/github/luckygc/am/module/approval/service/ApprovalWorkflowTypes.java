package github.luckygc.am.module.approval.service;

import java.util.List;

import org.jspecify.annotations.Nullable;

import github.luckygc.am.module.approval.ApprovalAction;
import github.luckygc.am.module.approval.ApprovalCandidateStrategy;
import github.luckygc.am.module.approval.ApprovalConditionOperator;
import github.luckygc.am.module.approval.ApprovalNodeType;

public final class ApprovalWorkflowTypes {

    private ApprovalWorkflowTypes() {}

    public record ApprovalWorkflowGraph(
            List<ApprovalFlowNode> nodes, List<ApprovalFlowEdge> edges) {

        public ApprovalWorkflowGraph {
            nodes = nodes == null ? List.of() : List.copyOf(nodes);
            edges = edges == null ? List.of() : List.copyOf(edges);
        }
    }

    public record ApprovalFlowNode(
            String nodeCode,
            String nodeName,
            ApprovalNodeType nodeType,
            int x,
            int y,
            @Nullable ApprovalCandidateStrategy candidateStrategy,
            List<Long> candidateUserIds,
            List<ApprovalAction> allowedActions) {

        public ApprovalFlowNode {
            candidateUserIds = candidateUserIds == null ? List.of() : List.copyOf(candidateUserIds);
            allowedActions = allowedActions == null ? List.of() : List.copyOf(allowedActions);
        }
    }

    public record ApprovalFlowEdge(
            String edgeCode,
            String sourceNodeCode,
            String targetNodeCode,
            boolean defaultFlow,
            @Nullable ApprovalFlowCondition condition) {}

    public record ApprovalFlowCondition(
            String field, ApprovalConditionOperator operator, List<String> values) {

        public ApprovalFlowCondition {
            values = values == null ? List.of() : List.copyOf(values);
        }
    }
}
