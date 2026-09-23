package com.chengjing.assessment.scoring;

import java.time.Instant;

/** Immutable text-only view supplied by member C's interview module. */
public record AnswerSnapshot(
        String userId,
        String interviewId,
        String roleKey,
        String questionId,
        String answerId,
        String questionText,
        String answerText,
        Instant answeredAt
) {}
