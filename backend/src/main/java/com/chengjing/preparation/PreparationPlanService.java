package com.chengjing.preparation;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * 岗位准备计划：创建、查看、修改与删除都限定在本人账号内。
 * 计划只保存本人写下的目标与岗位要求，不保存真实简历原文。
 */
@Service
public class PreparationPlanService {
    private static final int ROLE_MIN = 2;
    private static final int ROLE_MAX = 40;
    private static final int TITLE_MIN = 2;
    private static final int TITLE_MAX = 60;
    private static final int GOAL_MIN = 5;
    private static final int GOAL_MAX = 200;
    private static final int JD_MAX = 2000;
    private static final int SCENARIO_ID_MAX = 64;
    private static final Set<String> STATUSES = Set.of(
            PreparationPlan.STATUS_PLANNING,
            PreparationPlan.STATUS_READY,
            PreparationPlan.STATUS_ARCHIVED);

    private final PreparationStore store;
    private final PreparationUserPort userPort;

    public PreparationPlanService(PreparationStore store, PreparationUserPort userPort) {
        this.store = store;
        this.userPort = userPort;
    }

    /** 对外视图：补上记录 ID 与时间，节点列表供 B03 继续扩展。 */
    public record PlanView(
            String id,
            String role,
            String title,
            String goal,
            String scenarioId,
            String jd,
            String status,
            List<PlanNode> nodes,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public PlanView create(String role, String title, String goal, String scenarioId, String jd) {
        String userId = currentUserId();
        String nextRole = required(role, ROLE_MIN, ROLE_MAX, "请选择目标岗位（2～40 字）");
        String nextTitle = required(title, TITLE_MIN, TITLE_MAX, "计划名称需要 2～60 字");
        String nextGoal = required(goal, GOAL_MIN, GOAL_MAX, "准备目标需要 5～200 字，写清这次准备要解决什么");
        String nextJd = optional(jd, JD_MAX, "岗位要求最多 2000 字");
        // 情境 ID 来自 B01 的岗位情境目录，这里只保存引用，不复制案例内容。
        String nextScenarioId = optional(scenarioId, SCENARIO_ID_MAX, "情境 ID 过长");
        PreparationPlan plan = new PreparationPlan(nextRole, nextTitle, nextGoal, nextScenarioId, nextJd,
                PreparationPlan.STATUS_PLANNING, List.of());
        return view(store.create(userId, PreparationStore.Kind.PLAN, plan));
    }

    /** 列表只返回当前账号自己的计划。 */
    public List<PlanView> mine() {
        return store.list(currentUserId(), PreparationStore.Kind.PLAN).stream().map(this::view).toList();
    }

    public PlanView require(String planId) {
        return view(requireEntry(planId));
    }

    public PlanView update(String planId, String title, String goal, String status) {
        PreparationStore.Entry entry = requireEntry(planId);
        PreparationPlan plan = payload(entry);
        String nextTitle = plan.title();
        String nextGoal = plan.goal();
        String nextStatus = plan.status();
        if (title != null && !title.isBlank()) {
            nextTitle = required(title, TITLE_MIN, TITLE_MAX, "计划名称需要 2～60 字");
        }
        if (goal != null && !goal.isBlank()) {
            nextGoal = required(goal, GOAL_MIN, GOAL_MAX, "准备目标需要 5～200 字，写清这次准备要解决什么");
        }
        if (status != null && !status.isBlank()) {
            String candidate = status.strip().toUpperCase();
            if (!STATUSES.contains(candidate)) {
                throw PreparationException.badRequest("计划状态只能是 PLANNING、READY 或 ARCHIVED");
            }
            nextStatus = candidate;
        }
        PreparationPlan next = plan.withBasics(nextTitle, nextGoal, nextStatus);
        return view(store.update(entry, next));
    }

    public String delete(String planId) {
        PreparationStore.Entry entry = requireEntry(planId);
        store.delete(entry.userId(), entry.id(), PreparationStore.Kind.PLAN);
        return entry.id();
    }

    PreparationStore.Entry requireEntry(String planId) {
        return store.requireOwned(currentUserId(), planId, PreparationStore.Kind.PLAN);
    }

    PreparationPlan payload(PreparationStore.Entry entry) {
        return (PreparationPlan) entry.payload();
    }

    PlanView view(PreparationStore.Entry entry) {
        PreparationPlan plan = payload(entry);
        return new PlanView(entry.id(), plan.role(), plan.title(), plan.goal(), plan.scenarioId(), plan.jd(),
                plan.status(), plan.nodes(), entry.createdAt(), entry.updatedAt());
    }

    String currentUserId() {
        String userId = userPort.requireUserId();
        if (userId == null || userId.isBlank()) {
            throw PreparationException.unavailable("当前用户接口尚未配置，请完成 A02 身份模块连接");
        }
        return userId;
    }

    private static String required(String value, int min, int max, String message) {
        String next = optional(value, max, message);
        if (next == null || next.length() < min) {
            throw PreparationException.badRequest(message);
        }
        return next;
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
