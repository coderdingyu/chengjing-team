package com.chengjing.assessment.rubric;

import java.util.List;

public record ScoreRubric(
        String version,
        List<DimensionRule> dimensions,
        List<ScoreAnchor> scale,
        List<String> evidenceRules
) {
    public record DimensionRule(String key, String label, String description) {}
    public record ScoreAnchor(int score, String anchor) {}
}
