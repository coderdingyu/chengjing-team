package com.chengjing.preparation;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * B 模块本人数据的存取层：计划、经历素材、练习与复习项都带账号 ID，
 * 任何读取都要先经过 requireOwned 做归属校验，因此别的账号拿不到本人的数据。
 * 当前是进程内存实现；E01 提供持久化接口后只替换这一层，服务与接口不变。
 */
@Component
public class PreparationStore {
    /** 记录种类。 */
    public enum Kind {
        PLAN("准备计划"),
        STORY("经历素材"),
        PRACTICE("练习记录"),
        REVIEW("复习项");

        private final String label;

        Kind(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public record Entry(
            String id,
            String userId,
            Kind kind,
            Object payload,
            Instant createdAt,
            Instant updatedAt
    ) {}

    private final Map<String, Entry> entries = new ConcurrentHashMap<>();

    public Entry create(String userId, Kind kind, Object payload) {
        requireUserId(userId);
        Objects.requireNonNull(payload, "payload");
        Instant now = Instant.now();
        Entry entry = new Entry(UUID.randomUUID().toString(), userId, kind, payload, now, now);
        entries.put(entry.id(), entry);
        return entry;
    }

    /** 按 ID 取本人记录：不存在的 ID、类型不符或属于其他账号的记录都不会被返回。 */
    public Entry requireOwned(String userId, String id, Kind kind) {
        requireUserId(userId);
        Entry entry = entries.get(id == null ? "" : id);
        if (entry == null || entry.kind() != kind) {
            throw PreparationException.notFound(kind.label() + "不存在");
        }
        if (!entry.userId().equals(userId)) {
            throw PreparationException.forbidden("这条" + kind.label() + "属于其他账号，不能查看或修改");
        }
        return entry;
    }

    public Entry update(Entry entry, Object payload) {
        Objects.requireNonNull(payload, "payload");
        Entry next = new Entry(entry.id(), entry.userId(), entry.kind(), payload, entry.createdAt(), Instant.now());
        entries.put(next.id(), next);
        return next;
    }

    /** 只列出该账号自己的记录，最近更新的在前。 */
    public List<Entry> list(String userId, Kind kind) {
        requireUserId(userId);
        List<Entry> mine = new ArrayList<>();
        for (Entry entry : entries.values()) {
            if (entry.userId().equals(userId) && entry.kind() == kind) {
                mine.add(entry);
            }
        }
        mine.sort(Comparator.comparing(Entry::updatedAt).reversed());
        return List.copyOf(mine);
    }

    public void delete(String userId, String id, Kind kind) {
        Entry entry = requireOwned(userId, id, kind);
        entries.remove(entry.id());
    }

    private void requireUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalStateException("个人数据必须带账号归属");
        }
    }
}
