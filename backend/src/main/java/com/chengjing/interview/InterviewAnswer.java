package com.chengjing.interview;

import java.time.Instant;

/**
 * Owner: member C (interview module).
 *
 * The interview module's output for member D (module contract §C: "输出题目、回答文本、澄清意图与会话状态给 D").
 *
 * Only the text the candidate really submitted is carried here — no audio, no video, no visual
 * features. {@code gradeStatus} stays {@code PENDING} until D fills it in; C never invents a score.
 */
public record InterviewAnswer(
        String id,
        String questionId,
        String question,
        String text,
        String intent,
        String source,
        Instant answeredAt) {

    /** Waiting for member D to score. */
    public static final String GRADE_PENDING = "PENDING";

    public static final String SOURCE_TEXT = "TEXT";
}
