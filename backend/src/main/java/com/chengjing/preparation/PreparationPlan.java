package com.chengjing.preparation;

import java.util.List;

/**
 * 一份岗位准备计划：属于创建它的账号。
 * 深挖节点由 B03 依据岗位情境生成，这里保持列表，便于同一份计划反复扩展。
 */
public record PreparationPlan(
        String role,
        String title,
        String goal,
        String scenarioId,
        String jd,
        String status,
        List<PlanNode> nodes
) {
    public static final String STATUS_PLANNING = "PLANNING";
    public static final String STATUS_READY = "READY";
    public static final String STATUS_ARCHIVED = "ARCHIVED";

    public PreparationPlan withBasics(String nextTitle, String nextGoal, String nextStatus) {
        return new PreparationPlan(role, nextTitle, nextGoal, scenarioId, jd, nextStatus, nodes);
    }

    public PreparationPlan withNodes(List<PlanNode> nextNodes) {
        return new PreparationPlan(role, title, goal, scenarioId, jd, status, List.copyOf(nextNodes));
    }
}
