package com.chengjing.interview;

import java.util.List;
import java.util.Optional;

/**
 * Owner: member C. C06 — the three-dimensional interviewers a room can switch between.
 *
 * The catalog lives on the server so the room remembers the choice across a refresh, and so the
 * palette the 3D scene uses is data rather than something the browser invents. Three looks are
 * required by the acceptance rule ("可切换至少 3 个形象"); this catalog holds exactly three and the
 * test suite asserts the floor rather than the exact number, so adding a fourth stays allowed.
 */
public final class InterviewAvatar {

    /**
     * One interviewer look.
     *
     * @param id     stable identifier sent back when the candidate switches
     * @param name   what the room shows
     * @param trait  a one-line description of the interviewing style
     * @param accent hex colour used for the figure and its highlight
     * @param build  coarse body shape the scene builder maps to proportions
     */
    public record Look(String id, String name, String trait, String accent, String build) {
    }

    /** Used when a room is created and the candidate has not picked yet. */
    public static final String DEFAULT_ID = "chengqing";

    private static final List<Look> LOOKS = List.of(
            new Look("chengqing", "澄晴", "温和稳健，喜欢把回答追问到细节", "#6a8f65", "slim"),
            new Look("boran", "博然", "节奏明快，偏好结构化的回答", "#5b7fa6", "broad"),
            new Look("shuyi", "书仪", "关注业务结果与团队协作", "#a6715b", "tall"));

    private InterviewAvatar() {
    }

    public static List<Look> all() {
        return LOOKS;
    }

    public static boolean isKnown(String id) {
        return id != null && LOOKS.stream().anyMatch(look -> look.id().equals(id));
    }

    public static Optional<Look> find(String id) {
        return LOOKS.stream().filter(look -> look.id().equals(id)).findFirst();
    }
}
