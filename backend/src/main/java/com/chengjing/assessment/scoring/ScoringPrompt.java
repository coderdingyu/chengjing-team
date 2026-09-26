package com.chengjing.assessment.scoring;

import com.chengjing.assessment.rubric.ScoreRubric;

public record ScoringPrompt(
        String userId,
        String roleKey,
        String questionText,
        String answerText,
        ScoreRubric rubric
) {}
