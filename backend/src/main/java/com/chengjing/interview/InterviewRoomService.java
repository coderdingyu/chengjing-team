package com.chengjing.interview;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Owner: member C (interview module).
 *
 * C01 — create and reopen a complete interview room.
 *
 * Acceptance: a room is created from a role and a target duration, a refreshed page can reopen the
 * caller's own room, and a room belonging to somebody else is refused.
 */
@Service
public class InterviewRoomService {

    private final InterviewRoomStore store;
    private final CurrentUserPort currentUser;

    public InterviewRoomService(InterviewRoomStore store, CurrentUserPort currentUser) {
        this.store = store;
        this.currentUser = currentUser;
    }

    /** Request body of {@code POST /api/v1/interviews}. */
    public record CreateRequest(String role, String level, Integer minutes, String focus, String caseId) {
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
        view.put("stage", Map.of(
                "title", stage.title(),
                "prompt", stage.prompt(),
                "material", stage.material()));
        view.put("createdAt", room.createdAt().toString());
        return view;
    }
}
