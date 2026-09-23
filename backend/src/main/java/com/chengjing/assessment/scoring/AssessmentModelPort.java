package com.chengjing.assessment.scoring;

/**
 * Adapter point for member E's text model gateway. The platform module owns provider selection,
 * credentials, retries and structured-output parsing; assessment owns evidence validation.
 */
public interface AssessmentModelPort {
    CandidateReview score(ScoringPrompt prompt);
}
