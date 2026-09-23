package com.chengjing.preparation;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * 简历深挖节点：为本人计划生成「项目背景／个人行动／取舍判断／结果验证」四类节点，
 * 每个节点的状态与内容都能分别保存。节点挂在计划上，因此归属校验复用计划的归属校验。
 */
@Service
public class PlanNodeService {
    private static final int FOCUS_MAX = 60;
    private static final int CONTENT_MAX = 4000;
    private static final Set<String> STATUSES = Set.of(
            PlanNode.STATUS_TODO,
            PlanNode.STATUS_DRAFTING,
            PlanNode.STATUS_DONE);

    private final PreparationStore store;
    private final PreparationPlanService planService;

    public PlanNodeService(PreparationStore store, PreparationPlanService planService) {
        this.store = store;
        this.planService = planService;
    }

    /** 对外的节点视图：把计划归属一并带出，方便前端一次拿到完整上下文。 */
    public record NodeView(String planId, String role, PlanNode node) {}

    public List<NodeView> list(String planId) {
        PreparationPlan plan = plan(planId);
        return plan.nodes().stream().map(node -> new NodeView(planId, plan.role(), node)).toList();
    }

    /**
     * 生成节点：同一份计划重复调用不会重复堆叠节点，已填写的状态与内容都会保留。
     * replace 为 true 时清空重来，供本人主动重做使用。
     */
    public List<NodeView> generate(String planId, String focus, boolean replace) {
        PreparationStore.Entry entry = planService.requireEntry(planId);
        PreparationPlan plan = planService.payload(entry);
        String nextFocus = optional(focus, FOCUS_MAX, "经历方向最多 60 字");
        List<PlanNode> existing = plan.nodes();
        List<PlanNode> nodes = replace || existing.isEmpty()
                ? fresh(plan.role(), nextFocus)
                : mergeMissing(existing, plan.role(), nextFocus);
        PreparationPlan saved = plan.withNodes(nodes);
        store.update(entry, saved);
        return saved.nodes().stream().map(node -> new NodeView(entry.id(), saved.role(), node)).toList();
    }

    /** 保存单个节点的状态与内容；两个字段都可以单独提交。 */
    public NodeView save(String planId, String nodeId, String status, String content) {
        PreparationStore.Entry entry = planService.requireEntry(planId);
        PreparationPlan plan = planService.payload(entry);
        PlanNode target = requireNode(plan, nodeId);
        Instant now = Instant.now();
        PlanNode next = target;
        if (status != null && !status.isBlank()) {
            String candidate = status.strip().toUpperCase();
            if (!STATUSES.contains(candidate)) {
                throw PreparationException.badRequest("节点状态只能是 TODO、DRAFTING 或 DONE");
            }
            next = next.withStatus(candidate, now);
        }
        if (content != null) {
            String nextContent = optional(content, CONTENT_MAX, "节点内容最多 4000 字");
            String nextStatus = next.status();
            // 写了内容但还没标记完成时，节点自然进入「草稿中」，避免状态与内容互相矛盾。
            if (nextStatus == null || PlanNode.STATUS_TODO.equals(nextStatus)) {
                nextStatus = PlanNode.STATUS_DRAFTING;
            }
            next = next.withContent(nextContent, nextStatus, now);
        }
        List<PlanNode> nodes = replaceNode(plan.nodes(), next);
        PreparationPlan saved = plan.withNodes(nodes);
        store.update(entry, saved);
        return new NodeView(entry.id(), saved.role(), next);
    }

    public String delete(String planId, String nodeId) {
        PreparationStore.Entry entry = planService.requireEntry(planId);
        PreparationPlan plan = planService.payload(entry);
        PlanNode target = requireNode(plan, nodeId);
        List<PlanNode> nodes = new ArrayList<>();
        for (PlanNode node : plan.nodes()) {
            if (!node.id().equals(target.id())) {
                nodes.add(node);
            }
        }
        store.update(entry, plan.withNodes(nodes));
        return target.id();
    }

    PreparationPlan plan(String planId) {
        return planService.payload(planService.requireEntry(planId));
    }

    private static PlanNode requireNode(PreparationPlan plan, String nodeId) {
        for (PlanNode node : plan.nodes()) {
            if (node.id().equals(nodeId)) {
                return node;
            }
        }
        throw PreparationException.notFound("深挖节点不存在");
    }

    private static List<PlanNode> replaceNode(List<PlanNode> nodes, PlanNode next) {
        List<PlanNode> out = new ArrayList<>();
        for (PlanNode node : nodes) {
            out.add(node.id().equals(next.id()) ? next : node);
        }
        return out;
    }

    private static List<PlanNode> fresh(String role, String focus) {
        Instant now = Instant.now();
        return ResumeNodeCatalog.forRole(role, focus).stream()
                .map(template -> new PlanNode(UUID.randomUUID().toString(), template.kind(), template.title(),
                        template.prompt(), PlanNode.STATUS_TODO, "", now))
                .toList();
    }

    /** 只补齐缺失的节点类型，已经讲过的内容一个字都不动。 */
    private static List<PlanNode> mergeMissing(List<PlanNode> existing, String role, String focus) {
        Instant now = Instant.now();
        List<PlanNode> out = new ArrayList<>(existing);
        for (ResumeNodeCatalog.NodeTemplate template : ResumeNodeCatalog.forRole(role, focus)) {
            boolean present = existing.stream().anyMatch(node -> Objects.equals(node.kind(), template.kind()));
            if (!present) {
                out.add(new PlanNode(UUID.randomUUID().toString(), template.kind(), template.title(),
                        template.prompt(), PlanNode.STATUS_TODO, "", now));
            }
        }
        return out;
    }

    private static String optional(String value, int max, String message) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String next = value.strip();
        if (next.length() > max) {
            throw PreparationException.badRequest(message);
        }
        return next;
    }
}
