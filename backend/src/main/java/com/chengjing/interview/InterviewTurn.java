package com.chengjing.interview;

import java.time.Instant;

/**
 * Owner: member C (interview module).
 *
 * One line of the conversation. {@code seq} is a per-room sequence number, so the transcript has a
 * definite order even when two requests arrive close together, and {@code requestId} is what makes a
 * repeated submission idempotent.
 */
public record InterviewTurn(
        String id,
        int seq,
        String questionId,
        String role,
        String text,
        String intent,
        String requestId,
        Instant at) {

    public static final String ROLE_CANDIDATE = "candidate";
    public static final String ROLE_INTERVIEWER = "interviewer";

    /** Answer text, as stored, is treated strictly as data. */
    public boolean isCandidate() {
        return ROLE_CANDIDATE.equals(role);
    }
}
