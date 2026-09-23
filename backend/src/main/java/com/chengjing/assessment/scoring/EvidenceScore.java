package com.chengjing.assessment.scoring;

import com.chengjing.assessment.rubric.AssessmentDimension;
import com.chengjing.assessment.rubric.CoverageStatus;

import java.time.Instant;
import java.util.List;

public record EvidenceScore(
        String answerId,
        String questionId,
        String rubricVersion,
        String modelVersion,
        Instant generatedAt,
        Integer overallPercent,
        int coveredDimensions,
        String summary,
        List<DimensionScore> dimensions,
        List<String> gaps,
        String nextAction
) {
    public record DimensionScore(
            AssessmentDimension dimension,
            String label,
            CoverageStatus status,
            Integer score,
            String quote,
            int quoteStart,
            int quoteEnd,
            String reason
    ) {}
}
