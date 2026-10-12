package com.chengjing.interview;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Owner: member C (interview module).
 *
 * In-process room store. It exists so C01 can be verified end to end before E01 provides the shared
 * persistence port; swapping the implementation is a store-level change and leaves the service and
 * controller untouched.
 */
@Component
public class InterviewRoomStore {

    private final Map<String, InterviewRoom> rooms = new ConcurrentHashMap<>();

    public InterviewRoom save(InterviewRoom room) {
        rooms.put(room.id(), room);
        return room;
    }

    public Optional<InterviewRoom> find(String roomId) {
        return Optional.ofNullable(rooms.get(roomId));
    }

    /**
     * Resolves a room for its owner.
     *
     * A room that exists but belongs to somebody else is reported as absent rather than forbidden,
     * so the endpoint never confirms that another candidate's room exists.
     */
    public InterviewRoom owned(String userId, String roomId) {
        InterviewRoom room = roomId == null ? null : rooms.get(roomId);
        if (room == null || !room.userId().equals(userId)) {
            throw InterviewException.notFound("面试房间不存在");
        }
        return room;
    }

    /** Newest first, so "继续上次面试" is the first entry the lobby lists. */
    public List<InterviewRoom> mine(String userId) {
        return rooms.values().stream()
                .filter(room -> room.userId().equals(userId))
                .sorted(Comparator.comparing(InterviewRoom::createdAt).reversed())
                .toList();
    }

    /** Test helper: drops everything. Only used by the module's own tests. */
    void clear() {
        rooms.clear();
    }
}
