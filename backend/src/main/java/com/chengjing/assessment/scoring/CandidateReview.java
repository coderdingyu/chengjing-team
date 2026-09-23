package com.chengjing.assessment.scoring;

import com.chengjing.assessment.rubric.AssessmentDimension;

import java.util.List;

/** Untrusted structured output from the model gateway. */
public record CandidateReview(
        String modelVersion,
        String summary,
        List<CandidateDimension> dimensions,
        List<String> gaps,
        String nextAction
) {
    public record CandidateDimension(
            AssessmentDimension dimension,
            Integer score,
            String quote,
            String reason
    ) {}
}
