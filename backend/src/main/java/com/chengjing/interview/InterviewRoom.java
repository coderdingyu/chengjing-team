package com.chengjing.interview;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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
    private final List<InterviewTurn> turns = new ArrayList<>();
    private final List<InterviewAnswer> answers = new ArrayList<>();
    /** Identifiers already applied, so a retried submission never produces a second answer. */
    private final Set<String> appliedRequests = new LinkedHashSet<>();

    private String status = ACTIVE;
    private int stageIndex;
    private int followUps;
    private int turnSeq;
    /** Unsent answer text, kept so a refresh does not lose what the candidate was writing (C04). */
    private String draft = "";
    /** Seconds already banked; the live part is added while the room is ACTIVE. */
    private long elapsedSeconds;
    /** Same idea as {@link #elapsedSeconds}, but scoped to the stage on screen (C05). */
    private long stageElapsedSeconds;
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

    /**
     * Stable identifier of the question currently on screen. A turn carries it so a page that was
     * left open across a stage change cannot silently attach its answer to the wrong question.
     */
    public String currentQuestionId() {
        return id + ":stage-" + stageIndex;
    }

    public List<InterviewTurn> turns() {
        return turns;
    }

    public List<InterviewAnswer> answers() {
        return answers;
    }

    public int nextTurnSeq() {
        return ++turnSeq;
    }

    public int followUps() {
        return followUps;
    }

    void setFollowUps(int followUps) {
        this.followUps = followUps;
    }

    public boolean hasApplied(String requestId) {
        return appliedRequests.contains(requestId);
    }

    void rememberRequest(String requestId) {
        appliedRequests.add(requestId);
    }

    /** The turn already stored for a request id, or {@code null} when the request is new. */
    public InterviewTurn turnForRequest(String requestId) {
        return turns.stream().filter(turn -> requestId.equals(turn.requestId())).findFirst().orElse(null);
    }

    public long elapsedSeconds() {
        if (ACTIVE.equals(status)) {
            return elapsedSeconds + Duration.between(activeSince, Instant.now()).getSeconds();
        }
        return elapsedSeconds;
    }

    /** Stops the clock and marks the room paused, so no further answering time is counted (C04). */
    void pause() {
        bankElapsed();
        status = PAUSED;
    }

    void resume() {
        activeSince = Instant.now();
        stageStartedAt = activeSince;
        status = ACTIVE;
    }

    /** Terminal: the room is closed and its clock is frozen for the review page. */
    void finish(Instant at) {
        bankElapsed();
        status = COMPLETED;
        endedAt = at;
    }

    /** Moves the elapsed time already spent into the bank and restarts the live segment. */
    private void bankElapsed() {
        if (ACTIVE.equals(status)) {
            elapsedSeconds += Duration.between(activeSince, Instant.now()).getSeconds();
            stageElapsedSeconds += Duration.between(stageStartedAt, Instant.now()).getSeconds();
            activeSince = Instant.now();
            stageStartedAt = activeSince;
        }
    }

    /** Seconds spent on the current stage; frozen while paused or finished. */
    public long stageElapsedSeconds() {
        if (ACTIVE.equals(status)) {
            return stageElapsedSeconds + Duration.between(stageStartedAt, Instant.now()).getSeconds();
        }
        return stageElapsedSeconds;
    }

    /**
     * C05 — moves to the next stage.
     *
     * The clock for the new stage starts at zero, but the draft is deliberately left untouched: a
     * candidate who typed a little and then advanced must not lose the text.
     */
    void nextStage() {
        bankElapsed();
        stageIndex = Math.min(stageIndex + 1, stages.size() - 1);
        stageElapsedSeconds = 0;
        stageStartedAt = Instant.now();
    }

    public Instant activeSince() {
        return activeSince;
    }

    public Instant stageStartedAt() {
        return stageStartedAt;
    }

    public Instant endedAt() {
        return endedAt;
    }

    /** Unsent text the candidate is working on. Restored on refresh; cleared when an answer lands. */
    public String draft() {
        return draft;
    }

    void setDraft(String draft) {
        this.draft = draft == null ? "" : draft;
    }
}
