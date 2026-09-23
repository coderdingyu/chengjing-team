package com.chengjing.preparation;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * 个人经历素材库：新增、编辑、搜索、删除都限定在本人账号内。
 * 素材分成两类并各自标记：本人确认过的真实经历，与从公开岗位情境推演出来的模拟情境。
 */
@Service
public class StoryLibraryService {
    private static final int TITLE_MIN = 2;
    private static final int TITLE_MAX = 60;
    private static final int CONTENT_MIN = 5;
    private static final int CONTENT_MAX = 4000;
    private static final int ROLE_MAX = 40;
    private static final int TAG_MAX = 20;
    private static final int TAGS_LIMIT = 8;
    private static final int KEYWORD_MAX = 40;
    private static final Set<String> SOURCES = Set.of(StoryCard.SOURCE_SELF, StoryCard.SOURCE_SCENARIO);

    private final PreparationStore store;
    private final PreparationUserPort userPort;

    public StoryLibraryService(PreparationStore store, PreparationUserPort userPort) {
        this.store = store;
        this.userPort = userPort;
    }

    /** 对外视图：补上记录 ID 与时间，并给出给前端直接展示的标记文案。 */
    public record StoryView(
            String id,
            String title,
            String content,
            String source,
            boolean confirmedByOwner,
            String sourceLabel,
            List<String> tags,
            String role,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public StoryView create(String title, String content, String source, String role, List<String> tags) {
        String userId = currentUserId();
        String nextSource = sourceOf(source);
        StoryCard card = new StoryCard(
                required(title, TITLE_MIN, TITLE_MAX, "素材标题需要 2～60 字"),
                required(content, CONTENT_MIN, CONTENT_MAX, "素材内容需要 5～4000 字"),
                nextSource,
                false,
                cleanTags(tags),
                optional(role, ROLE_MAX, "岗位最多 40 字"));
        return view(store.create(userId, PreparationStore.Kind.STORY, card));
    }

    /** 列表与搜索只返回本人素材；keyword 命中标题、内容、岗位或标签。 */
    public List<StoryView> search(String keyword, String source) {
        String userId = currentUserId();
        String needle = keyword == null ? "" : keyword.strip().toLowerCase(Locale.ROOT);
        if (needle.length() > KEYWORD_MAX) {
            throw PreparationException.badRequest("搜索词最多 40 字");
        }
        String wanted = source == null || source.isBlank() ? null : sourceOf(source);
        List<StoryView> out = new ArrayList<>();
        for (PreparationStore.Entry entry : store.list(userId, PreparationStore.Kind.STORY)) {
            StoryCard card = (StoryCard) entry.payload();
            if (wanted != null && !wanted.equals(card.source())) {
                continue;
            }
            if (!needle.isEmpty() && !matches(card, needle)) {
                continue;
            }
            out.add(view(entry));
        }
        return List.copyOf(out);
    }

    public StoryView require(String storyId) {
        return view(entry(storyId));
    }

    public StoryView update(String storyId, String title, String content, String source, String role,
            List<String> tags) {
        PreparationStore.Entry entry = entry(storyId);
        StoryCard card = (StoryCard) entry.payload();
        String nextTitle = card.title();
        String nextContent = card.content();
        String nextSource = card.source();
        String nextRole = card.role();
        List<String> nextTags = card.tags();
        boolean nextConfirmed = card.confirmedByOwner();
        if (title != null && !title.isBlank()) {
            nextTitle = required(title, TITLE_MIN, TITLE_MAX, "素材标题需要 2～60 字");
        }
        if (content != null) {
            nextContent = required(content, CONTENT_MIN, CONTENT_MAX, "素材内容需要 5～4000 字");
        }
        if (source != null && !source.isBlank()) {
            nextSource = sourceOf(source);
        }
        if (role != null) {
            nextRole = optional(role, ROLE_MAX, "岗位最多 40 字");
        }
        if (tags != null) {
            nextTags = cleanTags(tags);
        }
        // 改成模拟情境时，之前对真实经历的确认必须一并撤销。
        if (!StoryCard.SOURCE_SELF.equals(nextSource)) {
            nextConfirmed = false;
        }
        StoryCard next = card.withBasics(nextTitle, nextContent, nextSource, nextConfirmed, nextTags, nextRole);
        return view(store.update(entry, next));
    }

    /** 本人核对后确认这是真实发生过的经历；模拟情境永远不能确认成事实。 */
    public StoryView confirm(String storyId, boolean confirmed) {
        PreparationStore.Entry entry = entry(storyId);
        StoryCard card = (StoryCard) entry.payload();
        if (confirmed && !StoryCard.SOURCE_SELF.equals(card.source())) {
            throw PreparationException.conflict("模拟情境不能标记为本人确认的事实，请先把它改成本人真实经历");
        }
        StoryCard next = card.withBasics(card.title(), card.content(), card.source(), confirmed, card.tags(),
                card.role());
        return view(store.update(entry, next));
    }

    public String delete(String storyId) {
        PreparationStore.Entry entry = entry(storyId);
        store.delete(entry.userId(), entry.id(), PreparationStore.Kind.STORY);
        return entry.id();
    }

    PreparationStore.Entry entry(String storyId) {
        return store.requireOwned(currentUserId(), storyId, PreparationStore.Kind.STORY);
    }

    StoryView view(PreparationStore.Entry entry) {
        StoryCard card = (StoryCard) entry.payload();
        return new StoryView(entry.id(), card.title(), card.content(), card.source(), card.confirmedByOwner(),
                labelOf(card), card.tags(), card.role(), entry.createdAt(), entry.updatedAt());
    }

    private static String labelOf(StoryCard card) {
        if (StoryCard.SOURCE_SELF.equals(card.source())) {
            return card.confirmedByOwner() ? "本人确认的事实" : "本人经历（待核对）";
        }
        return "模拟情境（公开案例推演）";
    }

    private static boolean matches(StoryCard card, String needle) {
        if (card.title().toLowerCase(Locale.ROOT).contains(needle)
                || card.content().toLowerCase(Locale.ROOT).contains(needle)) {
            return true;
        }
        if (card.role() != null && card.role().toLowerCase(Locale.ROOT).contains(needle)) {
            return true;
        }
        for (String tag : card.tags()) {
            if (tag.toLowerCase(Locale.ROOT).contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static String sourceOf(String source) {
        if (source == null || source.isBlank()) {
            throw PreparationException.badRequest("请选择素材来源：本人经历或模拟情境");
        }
        String candidate = source.strip().toUpperCase(Locale.ROOT);
        if (!SOURCES.contains(candidate)) {
            throw PreparationException.badRequest("素材来源只能是 SELF（本人经历）或 SCENARIO（模拟情境）");
        }
        return candidate;
    }

    private static List<String> cleanTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (String tag : tags) {
            if (tag == null || tag.isBlank()) {
                continue;
            }
            String next = tag.strip();
            if (next.length() > TAG_MAX) {
                throw PreparationException.badRequest("单个标签最多 20 字");
            }
            out.add(next);
            if (out.size() > TAGS_LIMIT) {
                throw PreparationException.badRequest("标签最多 8 个");
            }
        }
        return List.copyOf(out);
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
