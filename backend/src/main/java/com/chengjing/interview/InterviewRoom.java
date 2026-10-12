package com.chengjing.interview;

import java.time.Instant;
import java.util.List;

/**
 * Owner: member C (interview module).
 *
 * One candidate's interview room. This is a domain object, not a JPA entity: the skeleton has no
 * persistence dependency yet, so rooms live in {@link InterviewRoomStore} and E01 will later back
 * that store with the shared persistence port without changing this shape.
 *
 * Everything a personal record needs is here: the owning account id (module contract §3), the
 * creation time, the plan the room was opened with, and the state machine the room is in.
 */
public class InterviewRoom {

    /** The room accepts answers and is running its clock. */
    public static final String ACTIVE = "ACTIVE";
    /** The room is frozen: its clock stopped and no answer is accepted until it resumes. */
    public static final String PAUSED = "PAUSED";
    /** Terminal state. Ending a room is irreversible. */
    public static final String COMPLETED = "COMPLETED";

    private final String id;
    private final String userId;
    private final String role;
    private final String level;
    private final int minutes;
    private final String focus;
    private final String caseId;
    private final String title;
    private final Instant createdAt;
    private final List<InterviewStage> stages;

    private String status = ACTIVE;
    private int stageIndex;
    private long elapsedSeconds;
    private Instant activeSince = Instant.now();
    private Instant stageStartedAt = Instant.now();
    private Instant endedAt;

    public InterviewRoom(String id, String userId, String role, String level, int minutes,
                         String focus, String caseId, String title, Instant createdAt,
                         List<InterviewStage> stages) {
        this.id = id;
        this.userId = userId;
        this.role = role;
        this.level = level;
        this.minutes = minutes;
        this.focus = focus;
        this.caseId = caseId;
        this.title = title;
        this.createdAt = createdAt;
        this.stages = stages;
    }

    public String id() {
        return id;
    }

    public String userId() {
        return userId;
    }

    public String role() {
        return role;
    }

    public String level() {
        return level;
    }

    public int minutes() {
        return minutes;
    }

    public String focus() {
        return focus;
    }

    public String caseId() {
        return caseId;
    }

    public String title() {
        return title;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public List<InterviewStage> stages() {
        return stages;
    }

    public int totalStages() {
        return stages.size();
    }

    public String status() {
        return status;
    }

    void setStatus(String status) {
        this.status = status;
    }

    public int stageIndex() {
        return stageIndex;
    }

    void setStageIndex(int stageIndex) {
        this.stageIndex = stageIndex;
    }

    /** The stage currently on screen; the last stage stays current after the final answer. */
    public InterviewStage currentStage() {
        return stages.get(Math.min(stageIndex, stages.size() - 1));
    }

    public boolean isLastStage() {
        return stageIndex >= stages.size() - 1;
    }

    public long elapsedSeconds() {
        return elapsedSeconds;
    }

    void setElapsedSeconds(long elapsedSeconds) {
        this.elapsedSeconds = elapsedSeconds;
    }

    public Instant activeSince() {
        return activeSince;
    }

    void setActiveSince(Instant activeSince) {
        this.activeSince = activeSince;
    }

    public Instant stageStartedAt() {
        return stageStartedAt;
    }

    void setStageStartedAt(Instant stageStartedAt) {
        this.stageStartedAt = stageStartedAt;
    }

    public Instant endedAt() {
        return endedAt;
    }

    void setEndedAt(Instant endedAt) {
        this.endedAt = endedAt;
    }
}
