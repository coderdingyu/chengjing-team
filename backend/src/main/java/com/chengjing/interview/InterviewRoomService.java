package com.chengjing.interview;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * Owner: member C (interview module).
 *
 * C01 — create and reopen a complete interview room.
 * C02 — store a text answer, keep the transcript in order and make repeated submissions idempotent.
 *
 * Acceptance:
 * <ul>
 *   <li>a room is created from a role and a target duration;</li>
 *   <li>a refreshed page can reopen the caller's own room, and somebody else's room is refused;</li>
 *   <li>submitting an answer leaves ordered question/answer records and never stores two answers for
 *       one request, and candidate text is stored as data rather than obeyed as an instruction.</li>
 * </ul>
 */
@Service
public class InterviewRoomService {

    /** Same shape the collaboration guide uses for a client request id, so a retry is recognisable. */
    private static final Pattern REQUEST_ID = Pattern.compile("[A-Za-z0-9_-]{8,80}");

    private static final int MAX_ANSWER_LENGTH = 8000;
    private static final int MAX_TURNS_PER_ROOM = 120;

    private final InterviewRoomStore store;
    private final CurrentUserPort currentUser;
    private final InterviewDialoguePort dialogue;

    public InterviewRoomService(InterviewRoomStore store, CurrentUserPort currentUser,
                                InterviewDialoguePort dialogue) {
        this.store = store;
        this.currentUser = currentUser;
        this.dialogue = dialogue;
    }

    /** Request body of {@code POST /api/v1/interviews}. */
    public record CreateRequest(String role, String level, Integer minutes, String focus, String caseId) {
    }

    /** Request body of {@code POST /api/v1/interviews/{id}/turns}. */
    public record TurnRequest(String requestId, String questionId, String text) {
    }

    /**
     * Options the lobby needs before a room exists. Serving them from the backend keeps the form in
     * step with {@link InterviewCatalog} instead of duplicating the role list in the browser.
     */
    public Map<String, Object> catalog() {
        Map<String, Object> catalog = new LinkedHashMap<>();
        catalog.put("roles", InterviewCatalog.ROLES);
        catalog.put("levels", InterviewCatalog.LEVELS);
        catalog.put("durations", InterviewCatalog.DURATIONS_MINUTES);
        return catalog;
    }

    public Map<String, Object> create(CreateRequest request) {
        String userId = currentUser.requireUserId();

        String role = request.role() == null ? "" : request.role().trim();
        if (!InterviewCatalog.ROLES.contains(role)) {
            throw InterviewException.badRequest("请选择支持的岗位");
        }
        String level = request.level() == null || request.level().isBlank() ? "中级" : request.level().trim();
        if (!InterviewCatalog.LEVELS.contains(level)) {
            throw InterviewException.badRequest("请选择支持的面试级别");
        }
        int minutes = request.minutes() == null ? 15 : request.minutes();
        if (!InterviewCatalog.DURATIONS_MINUTES.contains(minutes)) {
            throw InterviewException.badRequest("请选择 15、30 或 45 分钟");
        }
        String focus = request.focus() == null ? "" : request.focus().trim();
        if (focus.length() > 500) {
            throw InterviewException.badRequest("关注点最多 500 字");
        }
        String caseId = request.caseId() == null || request.caseId().isBlank() ? null : request.caseId().trim();

        InterviewRoom room = new InterviewRoom(
                UUID.randomUUID().toString(),
                userId,
                role,
                level,
                minutes,
                focus,
                caseId,
                role + " · 模拟面试",
                Instant.now(),
                InterviewCatalog.deck(role, minutes));
        store.save(room);
        return view(room);
    }

    /** Rooms of the caller only; a room opened with another account never appears here. */
    public List<Map<String, Object>> mine() {
        return store.mine(currentUser.requireUserId()).stream().map(this::view).toList();
    }

    /** Reopening is what a browser refresh performs; it must return the same room, not a new one. */
    public Map<String, Object> reopen(String roomId) {
        return view(store.owned(currentUser.requireUserId(), roomId));
    }

    /**
     * Records a text answer and the interviewer's acknowledgement.
     *
     * The whole request is applied at most once: a repeated {@code requestId} returns the transcript
     * that is already stored instead of adding a second answer.
     */
    public Map<String, Object> submitTurn(String roomId, TurnRequest request) {
        String userId = currentUser.requireUserId();

        String requestId = request.requestId() == null ? "" : request.requestId().trim();
        if (!REQUEST_ID.matcher(requestId).matches()) {
            throw InterviewException.badRequest("缺少有效的提交标识，请重试");
        }
        String text = request.text() == null ? "" : request.text().trim();
        if (text.isEmpty()) {
            throw InterviewException.badRequest("请先写下回答再提交");
        }
        if (text.length() > MAX_ANSWER_LENGTH) {
            throw InterviewException.badRequest("单条回答最多 8000 字");
        }

        InterviewRoom room = store.owned(userId, roomId);
        synchronized (room) {
            InterviewTurn already = room.turnForRequest(requestId);
            if (already != null) {
                // Same request id: idempotent replay. A different body under the same id is a client bug.
                if (!already.text().equals(text)) {
                    throw InterviewException.conflict("该提交标识已被使用，请重新提交");
                }
                return turnResult(room, already, LocalInterviewDialogueAdapter.SOURCE);
            }

            requireActive(room);
            if (room.turns().size() >= MAX_TURNS_PER_ROOM) {
                throw InterviewException.conflict("本场对话已达上限，请结束并查看复盘");
            }
            String questionId = request.questionId() == null ? "" : request.questionId().trim();
            if (!room.currentQuestionId().equals(questionId)) {
                throw InterviewException.conflict("题目已更新，请刷新页面后继续");
            }

            InterviewAnswer answer = new InterviewAnswer(
                    UUID.randomUUID().toString(),
                    questionId,
                    room.currentStage().prompt(),
                    text,
                    "ANSWER",
                    InterviewAnswer.SOURCE_TEXT,
                    Instant.now());
            room.answers().add(answer);
            room.rememberRequest(requestId);

            InterviewTurn candidate = new InterviewTurn(
                    answer.id(),
                    room.nextTurnSeq(),
                    questionId,
                    InterviewTurn.ROLE_CANDIDATE,
                    text,
                    "ANSWER",
                    requestId,
                    answer.answeredAt());
            room.turns().add(candidate);

            // The evaluator is a stand-in until E03 lands; the source travels with the reply so the
            // client never presents it as a real model answer.
            InterviewDialoguePort.Reply reply = dialogue.reply(new InterviewDialoguePort.Context(
                    room.currentStage().title(),
                    room.currentStage().prompt(),
                    room.currentStage().material(),
                    text,
                    room.followUps(),
                    room.isLastStage()));

            InterviewTurn interviewer = new InterviewTurn(
                    UUID.randomUUID().toString(),
                    room.nextTurnSeq(),
                    questionId,
                    InterviewTurn.ROLE_INTERVIEWER,
                    reply.text(),
                    "RESPOND",
                    null,
                    Instant.now());
            room.turns().add(interviewer);

            return turnResult(room, candidate, reply.source());
        }
    }

    private void requireActive(InterviewRoom room) {
        if (InterviewRoom.COMPLETED.equals(room.status())) {
            throw InterviewException.conflict("本场面试已结束，不能再提交回答");
        }
        if (!InterviewRoom.ACTIVE.equals(room.status())) {
            throw InterviewException.conflict("面试已暂停，请继续后再作答");
        }
    }

    private Map<String, Object> turnResult(InterviewRoom room, InterviewTurn turn, String dialogueSource) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("turn", view(turn));
        result.put("dialogueSource", dialogueSource);
        result.put("answers", room.answers().stream().map(this::view).toList());
        result.put("transcript", transcript(room));
        result.put("stageIndex", room.stageIndex());
        result.put("totalStages", room.totalStages());
        result.put("status", room.status());
        return result;
    }

    /** Ordered conversation, oldest first, so a refreshed page rebuilds exactly what was said. */
    public List<Map<String, Object>> transcript(InterviewRoom room) {
        return withRoomLock(room, () -> room.turns().stream().map(this::view).toList());
    }

    /** Reads the conversation under the same lock the writer takes, so a snapshot is never torn. */
    private static <T> T withRoomLock(InterviewRoom room, java.util.function.Supplier<T> action) {
        synchronized (room) {
            return action.get();
        }
    }

    private Map<String, Object> view(InterviewTurn turn) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", turn.id());
        view.put("seq", turn.seq());
        view.put("questionId", turn.questionId());
        view.put("role", turn.role());
        view.put("text", turn.text());
        view.put("intent", turn.intent());
        view.put("at", turn.at().toString());
        return view;
    }

    private Map<String, Object> view(InterviewAnswer answer) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", answer.id());
        view.put("questionId", answer.questionId());
        view.put("question", answer.question());
        view.put("text", answer.text());
        view.put("intent", answer.intent());
        view.put("source", answer.source());
        view.put("answeredAt", answer.answeredAt().toString());
        view.put("gradeStatus", InterviewAnswer.GRADE_PENDING);
        return view;
    }

    Map<String, Object> view(InterviewRoom room) {
        InterviewStage stage = room.currentStage();
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", room.id());
        view.put("title", room.title());
        view.put("role", room.role());
        view.put("level", room.level());
        view.put("minutes", room.minutes());
        view.put("focus", room.focus());
        view.put("caseId", room.caseId());
        view.put("status", room.status());
        view.put("stageIndex", room.stageIndex());
        view.put("totalStages", room.totalStages());
        view.put("questionId", room.currentQuestionId());
        view.put("stage", Map.of(
                "title", stage.title(),
                "prompt", stage.prompt(),
                "material", stage.material()));
        view.put("turns", transcript(room));
        view.put("answers", withRoomLock(room, () -> room.answers().stream().map(this::view).toList()));
        view.put("createdAt", room.createdAt().toString());
        return view;
    }
}
