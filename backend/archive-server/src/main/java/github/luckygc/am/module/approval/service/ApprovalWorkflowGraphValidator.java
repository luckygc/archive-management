package github.luckygc.am.module.approval.service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import github.luckygc.am.common.exception.BadRequestException;
import github.luckygc.am.module.approval.ApprovalAction;
import github.luckygc.am.module.approval.ApprovalCandidateStrategy;
import github.luckygc.am.module.approval.ApprovalConditionOperator;
import github.luckygc.am.module.approval.ApprovalNodeType;
import github.luckygc.am.module.approval.service.ApprovalWorkflowTypes.ApprovalFlowCondition;
import github.luckygc.am.module.approval.service.ApprovalWorkflowTypes.ApprovalFlowEdge;
import github.luckygc.am.module.approval.service.ApprovalWorkflowTypes.ApprovalFlowNode;
import github.luckygc.am.module.approval.service.ApprovalWorkflowTypes.ApprovalWorkflowGraph;

@Component
public class ApprovalWorkflowGraphValidator {

    private static final Pattern CODE_PATTERN = Pattern.compile("[a-z][a-z0-9_-]{0,99}");
    private static final Pattern CONDITION_FIELD_PATTERN = Pattern.compile("[a-z][a-z0-9_]{0,63}");
    private static final int MAX_NODES = 500;
    private static final int MAX_EDGES = 1000;

    public ApprovalWorkflowGraph validateDraft(ApprovalWorkflowGraph source) {
        return validateAndNormalizeDraft(source);
    }

    private ApprovalWorkflowGraph validateAndNormalizeDraft(ApprovalWorkflowGraph source) {
        if (source == null) {
            throw invalid("graph", "流程图不能为空");
        }
        if (source.nodes().size() > MAX_NODES || source.edges().size() > MAX_EDGES) {
            throw invalid("graph", "流程图最多包含 500 个节点和 1000 条连线");
        }
        List<ApprovalFlowNode> nodes = new ArrayList<>(source.nodes().size());
        Set<String> nodeCodes = new HashSet<>();
        for (ApprovalFlowNode node : source.nodes()) {
            if (node == null || node.nodeType() == null) {
                throw invalid("graph.nodes", "节点和节点类型不能为空");
            }
            String nodeCode = requiredCode(node.nodeCode(), "graph.nodes.nodeCode");
            if (!nodeCodes.add(nodeCode)) {
                throw invalid("graph.nodes", "节点编码重复: " + nodeCode);
            }
            nodes.add(
                    new ApprovalFlowNode(
                            nodeCode,
                            requiredText(node.nodeName(), "graph.nodes.nodeName"),
                            node.nodeType(),
                            node.x(),
                            node.y(),
                            node.candidateStrategy(),
                            node.candidateUserIds(),
                            node.allowedActions()));
        }
        requireSingleNodeType(nodes, ApprovalNodeType.START, "流程图必须且只能包含一个开始节点");
        requireSingleNodeType(nodes, ApprovalNodeType.END, "流程图必须且只能包含一个结束节点");

        List<ApprovalFlowEdge> edges = new ArrayList<>(source.edges().size());
        Set<String> edgeCodes = new HashSet<>();
        for (ApprovalFlowEdge edge : source.edges()) {
            if (edge == null) {
                throw invalid("graph.edges", "连线不能为空");
            }
            String edgeCode = requiredCode(edge.edgeCode(), "graph.edges.edgeCode");
            if (!edgeCodes.add(edgeCode)) {
                throw invalid("graph.edges", "连线编码重复: " + edgeCode);
            }
            String sourceCode = requiredCode(edge.sourceNodeCode(), "graph.edges.sourceNodeCode");
            String targetCode = requiredCode(edge.targetNodeCode(), "graph.edges.targetNodeCode");
            if (!nodeCodes.contains(sourceCode) || !nodeCodes.contains(targetCode)) {
                throw invalid("graph.edges", "连线引用了不存在的节点: " + edgeCode);
            }
            if (sourceCode.equals(targetCode)) {
                throw invalid("graph.edges", "连线不能连接节点自身: " + edgeCode);
            }
            edges.add(
                    new ApprovalFlowEdge(
                            edgeCode,
                            sourceCode,
                            targetCode,
                            edge.defaultFlow(),
                            normalizeCondition(edge.condition())));
        }
        return new ApprovalWorkflowGraph(nodes, edges);
    }

    public ApprovalWorkflowGraph validateForPublishing(ApprovalWorkflowGraph source) {
        ApprovalWorkflowGraph graph = validateAndNormalizeDraft(source);
        Map<String, ApprovalFlowNode> nodes =
                graph.nodes().stream()
                        .collect(Collectors.toMap(ApprovalFlowNode::nodeCode, Function.identity()));
        if (graph.nodes().stream()
                .noneMatch(node -> node.nodeType() == ApprovalNodeType.APPROVAL)) {
            throw invalid("graph.nodes", "发布时至少需要一个审批节点");
        }

        Map<String, List<ApprovalFlowEdge>> outgoing = groupEdges(graph.edges(), true);
        Map<String, List<ApprovalFlowEdge>> incoming = groupEdges(graph.edges(), false);
        for (ApprovalFlowNode node : graph.nodes()) {
            List<ApprovalFlowEdge> nodeOutgoing = outgoing.getOrDefault(node.nodeCode(), List.of());
            List<ApprovalFlowEdge> nodeIncoming = incoming.getOrDefault(node.nodeCode(), List.of());
            validateNode(node, nodeIncoming, nodeOutgoing);
        }

        ApprovalFlowNode start = findNode(graph.nodes(), ApprovalNodeType.START);
        ApprovalFlowNode end = findNode(graph.nodes(), ApprovalNodeType.END);
        Set<String> reachable = traverse(start.nodeCode(), outgoing, true);
        Set<String> reachesEnd = traverse(end.nodeCode(), incoming, false);
        for (ApprovalFlowNode node : graph.nodes()) {
            if (!reachable.contains(node.nodeCode())) {
                throw invalid("graph.nodes", "节点无法从开始节点到达: " + node.nodeCode());
            }
            if (!reachesEnd.contains(node.nodeCode())) {
                throw invalid("graph.nodes", "节点无法到达结束节点: " + node.nodeCode());
            }
        }
        ensureAcyclic(graph, outgoing);
        return graph;
    }

    private void validateNode(
            ApprovalFlowNode node,
            List<ApprovalFlowEdge> incoming,
            List<ApprovalFlowEdge> outgoing) {
        switch (node.nodeType()) {
            case START -> {
                if (!incoming.isEmpty() || outgoing.size() != 1) {
                    throw invalid("graph.nodes", "开始节点必须没有入线且只有一条出线");
                }
                rejectApprovalProperties(node);
            }
            case END -> {
                if (incoming.isEmpty() || !outgoing.isEmpty()) {
                    throw invalid("graph.nodes", "结束节点必须至少有一条入线且没有出线");
                }
                rejectApprovalProperties(node);
            }
            case APPROVAL -> {
                if (incoming.isEmpty() || outgoing.size() != 1) {
                    throw invalid("graph.nodes", "审批节点必须至少有一条入线且只有一条出线: " + node.nodeCode());
                }
                if (node.candidateStrategy() != ApprovalCandidateStrategy.SPECIFIED_USERS) {
                    throw invalid("graph.nodes.candidateStrategy", "审批节点只支持指定用户候选人");
                }
                if (node.candidateUserIds().isEmpty()
                        || node.candidateUserIds().stream()
                                .anyMatch(userId -> userId == null || userId <= 0)
                        || node.candidateUserIds().stream().distinct().count()
                                != node.candidateUserIds().size()) {
                    throw invalid("graph.nodes.candidateUserIds", "审批节点候选用户必须是去重后的正整数 ID");
                }
                if (!node.allowedActions()
                        .containsAll(List.of(ApprovalAction.APPROVE, ApprovalAction.REJECT))) {
                    throw invalid("graph.nodes.allowedActions", "审批节点必须允许同意和驳回");
                }
                rejectConditions(outgoing, node.nodeCode());
            }
            case EXCLUSIVE_GATEWAY -> {
                rejectApprovalProperties(node);
                if (incoming.isEmpty() || outgoing.size() < 2) {
                    throw invalid("graph.nodes", "条件分支必须至少有一条入线和两条出线: " + node.nodeCode());
                }
                long defaults = outgoing.stream().filter(ApprovalFlowEdge::defaultFlow).count();
                if (defaults != 1) {
                    throw invalid("graph.edges", "条件分支必须且只能包含一条默认出线: " + node.nodeCode());
                }
                for (ApprovalFlowEdge edge : outgoing) {
                    if (edge.defaultFlow() && edge.condition() != null) {
                        throw invalid("graph.edges", "默认分支不能同时配置条件: " + edge.edgeCode());
                    }
                    if (!edge.defaultFlow() && edge.condition() == null) {
                        throw invalid("graph.edges", "非默认分支必须配置条件: " + edge.edgeCode());
                    }
                    validateCondition(edge.condition(), edge.edgeCode());
                }
            }
        }
        if (node.nodeType() != ApprovalNodeType.EXCLUSIVE_GATEWAY) {
            rejectConditions(outgoing, node.nodeCode());
        }
    }

    private void rejectApprovalProperties(ApprovalFlowNode node) {
        if (node.candidateStrategy() != null
                || !node.candidateUserIds().isEmpty()
                || !node.allowedActions().isEmpty()) {
            throw invalid("graph.nodes", "只有审批节点可以配置候选人和办理动作: " + node.nodeCode());
        }
    }

    private void rejectConditions(List<ApprovalFlowEdge> edges, String nodeCode) {
        if (edges.stream().anyMatch(edge -> edge.defaultFlow() || edge.condition() != null)) {
            throw invalid("graph.edges", "只有条件分支的出线可以配置条件或默认标记: " + nodeCode);
        }
    }

    private void validateCondition(ApprovalFlowCondition condition, String edgeCode) {
        if (condition == null) {
            return;
        }
        if (!CONDITION_FIELD_PATTERN.matcher(condition.field()).matches()) {
            throw invalid("graph.edges.condition.field", "条件字段编码不合法: " + edgeCode);
        }
        if (condition.operator() == null || condition.values().isEmpty()) {
            throw invalid("graph.edges.condition", "条件运算符和值不能为空: " + edgeCode);
        }
        if (condition.values().stream().anyMatch(StringUtils::isBlank)) {
            throw invalid("graph.edges.condition.values", "条件值不能为空: " + edgeCode);
        }
        if (condition.operator() != ApprovalConditionOperator.IN
                && condition.values().size() != 1) {
            throw invalid("graph.edges.condition.values", "等于和不等于条件只能包含一个值: " + edgeCode);
        }
    }

    private ApprovalFlowCondition normalizeCondition(ApprovalFlowCondition condition) {
        if (condition == null) {
            return null;
        }
        String field = requiredText(condition.field(), "graph.edges.condition.field").toLowerCase();
        List<String> values =
                condition.values() == null
                        ? List.of()
                        : condition.values().stream()
                                .map(value -> requiredText(value, "graph.edges.condition.values"))
                                .toList();
        return new ApprovalFlowCondition(field, condition.operator(), values);
    }

    private Map<String, List<ApprovalFlowEdge>> groupEdges(
            List<ApprovalFlowEdge> edges, boolean bySource) {
        return edges.stream()
                .collect(
                        Collectors.groupingBy(
                                bySource
                                        ? ApprovalFlowEdge::sourceNodeCode
                                        : ApprovalFlowEdge::targetNodeCode));
    }

    private Set<String> traverse(
            String start, Map<String, List<ApprovalFlowEdge>> adjacency, boolean forward) {
        Set<String> visited = new HashSet<>();
        ArrayDeque<String> queue = new ArrayDeque<>();
        queue.add(start);
        while (!queue.isEmpty()) {
            String current = queue.removeFirst();
            if (!visited.add(current)) {
                continue;
            }
            for (ApprovalFlowEdge edge : adjacency.getOrDefault(current, List.of())) {
                queue.add(forward ? edge.targetNodeCode() : edge.sourceNodeCode());
            }
        }
        return visited;
    }

    private void ensureAcyclic(
            ApprovalWorkflowGraph graph, Map<String, List<ApprovalFlowEdge>> outgoing) {
        Map<String, Integer> indegrees = new HashMap<>();
        graph.nodes().forEach(node -> indegrees.put(node.nodeCode(), 0));
        graph.edges()
                .forEach(
                        edge ->
                                indegrees.compute(
                                        edge.targetNodeCode(), (ignored, value) -> value + 1));
        ArrayDeque<String> queue = new ArrayDeque<>();
        indegrees.forEach(
                (nodeCode, degree) -> {
                    if (degree == 0) {
                        queue.add(nodeCode);
                    }
                });
        int visited = 0;
        while (!queue.isEmpty()) {
            String current = queue.removeFirst();
            visited++;
            for (ApprovalFlowEdge edge : outgoing.getOrDefault(current, List.of())) {
                int degree =
                        indegrees.compute(edge.targetNodeCode(), (ignored, value) -> value - 1);
                if (degree == 0) {
                    queue.add(edge.targetNodeCode());
                }
            }
        }
        if (visited != graph.nodes().size()) {
            throw invalid("graph", "审批流程不能包含环路");
        }
    }

    private void requireSingleNodeType(
            List<ApprovalFlowNode> nodes, ApprovalNodeType nodeType, String message) {
        if (nodes.stream().filter(node -> node.nodeType() == nodeType).count() != 1) {
            throw invalid("graph.nodes", message);
        }
    }

    private ApprovalFlowNode findNode(List<ApprovalFlowNode> nodes, ApprovalNodeType nodeType) {
        return nodes.stream()
                .filter(node -> node.nodeType() == nodeType)
                .findFirst()
                .orElseThrow(() -> invalid("graph.nodes", "流程图节点不存在"));
    }

    private String requiredCode(String value, String field) {
        String code = requiredText(value, field).toLowerCase();
        if (!CODE_PATTERN.matcher(code).matches()) {
            throw invalid(field, "编码必须以小写字母开头，且只能包含小写字母、数字、下划线或连字符");
        }
        return code;
    }

    private String requiredText(String value, String field) {
        if (StringUtils.isBlank(value)) {
            throw invalid(field, "必填字段不能为空");
        }
        return value.trim();
    }

    private BadRequestException invalid(String field, String message) {
        return new BadRequestException("审批流程图不合法", field, message);
    }
}
