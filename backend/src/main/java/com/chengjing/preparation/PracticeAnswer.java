package com.chengjing.preparation;

import java.time.Instant;

/**
 * 一次单题练习提交的文字回答。
 * 同一道题可以反复练，用 revision 记住这是第几次作答，requestId 用来识别重复提交。
 */
public record PracticeAnswer(
        String id,
        String requestId,
        String questionId,
        String question,
        String text,
        int revision,
        Instant answeredAt
) {}
