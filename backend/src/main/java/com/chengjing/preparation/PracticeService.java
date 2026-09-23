package com.chengjing.preparation;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * 单题专项练习：从简历深挖节点发起一道题，本人可以反复写草稿、反复提交文字回答。
 * 练习与草稿都只存在本人账号下，读写之前一律先过归属校验。
 */
@Service
public class PracticeService {
    private static final int ANSWER_MIN = 2;
    private static final int ANSWER_MAX = 4000;
    private static final int REQUEST_ID_MAX = 80;

    private final PreparationStore store;
    private final PreparationUserPort userPort;
    private final PlanNodeService nodeService;

    public PracticeService(PreparationStore store, PreparationUserPort userPort, PlanNodeService nodeService) {
        this.store = store;
        this.userPort = userPort;
        this.nodeService = nodeService;
    }

    /** 对外视图：题目、草稿、历次回答和当前状态一次给全，前端不需要再拼。 */
    public record PracticeView(
            String id,
            String planId,
            String nodeId,
            String nodeTitle,
            String questionId,
            String question,
            String status,
            String statusLabel,
            String draft,
            int attemptCount,
            List<PracticeAnswer> answers,
            Instant createdAt,
            Instant updatedAt
    ) {}

    /**
     * 从深挖地图的一个节点发起练习：题目就是该节点的提问。
     * 同一个节点重复进入会复用已有练习，草稿与历次回答都还在，不会堆出一串空记录。
     */
    public PracticeView create(String planId, String nodeId) {
        PreparationPlan plan = nodeService.plan(planId);
        PlanNode node = requireNode(plan, nodeId);
        String userId = currentUserId();
        Optional<PreparationStore.Entry> existing = store.list(userId, PreparationStore.Kind.PRACTICE).stream()
                .filter(entry -> sameNode(entry, planId, nodeId))
                .findFirst();
        if (existing.isPresent()) {
            return view(existing.get());
        }
        String question = node.prompt() == null || node.prompt().isBlank() ? node.title() : node.prompt().strip();
        PracticeSession session = new PracticeSession(planId, node.id(), node.title(),
                UUID.randomUUID().toString(), question, "", List.of());
        return view(store.create(userId, PreparationStore.Kind.PRACTICE, session));
    }

    /** 列表只返回本人练习，最近更新的在前。 */
    public List<PracticeView> mine() {
        return store.list(currentUserId(), PreparationStore.Kind.PRACTICE).stream().map(this::view).toList();
    }

    public PracticeView require(String practiceId) {
        return view(entry(practiceId));
    }

    /**
     * 保存草稿。写草稿是练习里最常做的动作，因此这里只校验长度：
     * 草稿超长会明确报错，输入框里的内容不会因此丢掉。
     */
    public PracticeView saveDraft(String practiceId, String draft) {
        PreparationStore.Entry entry = entry(practiceId);
        PracticeSession session = (PracticeSession) entry.payload();
        String next = optional(draft, ANSWER_MAX, "草稿最多 4000 字，先记下要点也可以");
        return view(store.update(entry, session.withDraft(next == null ? "" : next)));
    }

    /**
     * 提交文字回答。带同一个 requestId 的重复请求不会产生第二份回答；
     * 校验不通过时直接返回错误，草稿原样留在练习里，本人可以继续修改后再交。
     */
    public PracticeView submit(String practiceId, String text, String requestId) {
        PreparationStore.Entry entry = entry(practiceId);
        PracticeSession session = (PracticeSession) entry.payload();
        String answer = required(text, ANSWER_MIN, ANSWER_MAX, "回答需要 2～4000 字，写清你本人做过的事");
        String token = optional(requestId, REQUEST_ID_MAX, "提交标识最多 80 字");
        if (token != null) {
            Optional<PracticeAnswer> duplicate = session.answers().stream()
                    .filter(item -> token.equals(item.requestId()))
                    .findFirst();
            if (duplicate.isPresent()) {
                if (!duplicate.get().text().equals(answer)) {
                    throw PreparationException.conflict("这次提交已经保存了另一份回答，请刷新查看后再重新提交");
                }
                return view(entry);
            }
        }
        PracticeAnswer next = new PracticeAnswer(UUID.randomUUID().toString(), token, session.questionId(),
                session.question(), answer, session.answers().size() + 1, Instant.now());
        return view(store.update(entry, session.withAnswer(next)));
    }

    public String delete(String practiceId) {
        PreparationStore.Entry entry = entry(practiceId);
        store.delete(entry.userId(), entry.id(), PreparationStore.Kind.PRACTICE);
        return entry.id();
    }

    PreparationStore.Entry entry(String practiceId) {
        return store.requireOwned(currentUserId(), practiceId, PreparationStore.Kind.PRACTICE);
    }

    PracticeView view(PreparationStore.Entry entry) {
        PracticeSession session = (PracticeSession) entry.payload();
        return new PracticeView(entry.id(), session.planId(), session.nodeId(), session.nodeTitle(),
                session.questionId(), session.question(), session.status(), labelOf(session), session.draft(),
                session.answers().size(), session.answers(), entry.createdAt(), entry.updatedAt());
    }

    private static String labelOf(PracticeSession session) {
        return PracticeSession.STATUS_ANSWERED.equals(session.status()) ? "已提交回答" : "草稿中";
    }

    private static boolean sameNode(PreparationStore.Entry entry, String planId, String nodeId) {
        PracticeSession session = (PracticeSession) entry.payload();
        return session.planId().equals(planId) && session.nodeId().equals(nodeId);
    }

    private static PlanNode requireNode(PreparationPlan plan, String nodeId) {
        for (PlanNode node : plan.nodes()) {
            if (node.id().equals(nodeId)) {
                return node;
            }
        }
        throw PreparationException.notFound("深挖节点不存在，请先在深挖地图里生成节点");
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
