package com.chengjing.assessment.scoring;

/** Adapter point for member C's immutable, ordered text-answer records. */
public interface InterviewAnswerPort {
    AnswerSnapshot requireOwnedAnswer(String userId, String answerId);
}
