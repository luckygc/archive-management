package github.luckygc.am.module.approval.service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import github.luckygc.am.module.approval.service.ApprovalWorkflowTypes.ApprovalFlowCondition;
import github.luckygc.am.module.approval.service.ApprovalWorkflowTypes.ApprovalFlowEdge;
import github.luckygc.am.module.approval.service.ApprovalWorkflowTypes.ApprovalFlowNode;
import github.luckygc.am.module.approval.service.ApprovalWorkflowTypes.ApprovalWorkflowGraph;

@Component
public class ApprovalBpmnXmlGenerator {

    public String generate(String processKey, String processName, ApprovalWorkflowGraph graph) {
        Map<String, ApprovalFlowEdge> defaultFlows =
                graph.edges().stream()
                        .filter(ApprovalFlowEdge::defaultFlow)
                        .collect(
                                Collectors.toMap(
                                        ApprovalFlowEdge::sourceNodeCode, Function.identity()));
        StringBuilder xml =
                new StringBuilder(
                                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                                        + "<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\" "
                                        + "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" "
                                        + "xmlns:flowable=\"http://flowable.org/bpmn\" "
                                        + "targetNamespace=\"https://archive-management.local/approval\">"
                                        + "<process id=\"")
                        .append("process_")
                        .append(escape(processKey))
                        .append("\" name=\"")
                        .append(escape(processName))
                        .append("\" isExecutable=\"true\">");

        for (ApprovalFlowNode node : graph.nodes()) {
            appendNode(xml, node, defaultFlows.get(node.nodeCode()));
        }
        for (ApprovalFlowEdge edge : graph.edges()) {
            appendEdge(xml, edge);
        }
        return xml.append("</process></definitions>").toString();
    }

    private void appendNode(
            StringBuilder xml, ApprovalFlowNode node, ApprovalFlowEdge defaultFlow) {
        switch (node.nodeType()) {
            case START ->
                    xml.append("<startEvent id=\"")
                            .append(escape(node.nodeCode()))
                            .append("\" name=\"")
                            .append(escape(node.nodeName()))
                            .append("\"/>");
            case END ->
                    xml.append("<endEvent id=\"")
                            .append(escape(node.nodeCode()))
                            .append("\" name=\"")
                            .append(escape(node.nodeName()))
                            .append("\"/>");
            case APPROVAL ->
                    xml.append("<userTask id=\"")
                            .append(escape(node.nodeCode()))
                            .append("\" name=\"")
                            .append(escape(node.nodeName()))
                            .append("\" flowable:candidateUsers=\"")
                            .append(
                                    node.candidateUserIds().stream()
                                            .map(String::valueOf)
                                            .collect(Collectors.joining(",")))
                            .append("\"/>");
            case EXCLUSIVE_GATEWAY -> {
                xml.append("<exclusiveGateway id=\"")
                        .append(escape(node.nodeCode()))
                        .append("\" name=\"")
                        .append(escape(node.nodeName()))
                        .append("\"");
                if (defaultFlow != null) {
                    xml.append(" default=\"").append(escape(defaultFlow.edgeCode())).append("\"");
                }
                xml.append("/>");
            }
        }
    }

    private void appendEdge(StringBuilder xml, ApprovalFlowEdge edge) {
        xml.append("<sequenceFlow id=\"")
                .append(escape(edge.edgeCode()))
                .append("\" sourceRef=\"")
                .append(escape(edge.sourceNodeCode()))
                .append("\" targetRef=\"")
                .append(escape(edge.targetNodeCode()))
                .append("\"");
        ApprovalFlowCondition condition = edge.condition();
        if (condition == null) {
            xml.append("/>");
            return;
        }
        xml.append("><conditionExpression xsi:type=\"tFormalExpression\">")
                .append(escape(conditionExpression(condition)))
                .append("</conditionExpression></sequenceFlow>");
    }

    private String conditionExpression(ApprovalFlowCondition condition) {
        String encodedValues =
                condition.values().stream().map(this::encodeValue).collect(Collectors.joining("."));
        return "${approvalConditionEvaluator.matches(businessContext, '"
                + condition.field()
                + "', '"
                + condition.operator().name()
                + "', '"
                + encodedValues
                + "')}";
    }

    private String encodeValue(String value) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private String escape(String value) {
        return value.replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
