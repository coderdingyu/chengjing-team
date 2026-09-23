package com.chengjing.assessment.scoring;

import com.chengjing.assessment.AssessmentException;
import com.chengjing.assessment.rubric.AssessmentDimension;
import com.chengjing.assessment.rubric.CoverageStatus;
import com.chengjing.assessment.rubric.ScoreRubricService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EvidenceScoringServiceTest {
    private static final String ANSWER = "我负责把接口延迟从 800 毫秒降到 220 毫秒。"
            + "我比较了缓存和索引两种方案，先用压测确认索引命中后上线。";
    private static final AnswerSnapshot SNAPSHOT = new AnswerSnapshot(
            "user-1", "interview-1", "backend", "question-1", "answer-1",
            "请说明一次性能优化经历", ANSWER, Instant.parse("2026-09-23T08:00:00Z"));

    @Test
    void everyScoreKeepsAnExactQuoteAndMissingEvidenceIsUncovered() {
        CandidateReview review = review(List.of(
                evidenced(AssessmentDimension.QUESTION_GOAL, 3, "接口延迟从 800 毫秒降到 220 毫秒"),
                evidenced(AssessmentDimension.PERSONAL_ACTION, 3, "我负责把接口延迟"),
                evidenced(AssessmentDimension.ANALYSIS_TRADEOFF, 4, "比较了缓存和索引两种方案"),
                uncovered(AssessmentDimension.VALIDATION_REFLECTION)
        ));
        EvidenceScore result = service(prompt -> review).score(SNAPSHOT);

        assertEquals(3, result.coveredDimensions());
        assertNull(result.overallPercent(), "存在未覆盖维度时不能生成伪完整总分");
        assertEquals("比较了缓存和索引两种方案", result.dimensions().get(2).quote());
        assertEquals(CoverageStatus.UNCOVERED, result.dimensions().get(3).status());
        assertNull(result.dimensions().get(3).score());
        assertEquals(-1, result.dimensions().get(3).quoteStart());
    }

    @Test
    void rejectsAQuoteThatDoesNotExistInTheAnswer() {
        CandidateReview review = review(List.of(
                evidenced(AssessmentDimension.QUESTION_GOAL, 4, "最终性能提升了十倍"),
                evidenced(AssessmentDimension.PERSONAL_ACTION, 3, "我负责把接口延迟"),
                evidenced(AssessmentDimension.ANALYSIS_TRADEOFF, 4, "比较了缓存和索引两种方案"),
                uncovered(AssessmentDimension.VALIDATION_REFLECTION)
        ));

        AssessmentException error = assertThrows(
                AssessmentException.class, () -> service(prompt -> review).score(SNAPSHOT));
        assertEquals("评分结果无效：模型引文不在该回答原文中", error.getMessage());
    }

    @Test
    void rejectsZeroWhenTheDimensionHasNoEvidence() {
        CandidateReview review = review(List.of(
                new CandidateReview.CandidateDimension(
                        AssessmentDimension.QUESTION_GOAL, 0, "", "回答没有提到目标"),
                uncovered(AssessmentDimension.PERSONAL_ACTION),
                uncovered(AssessmentDimension.ANALYSIS_TRADEOFF),
                uncovered(AssessmentDimension.VALIDATION_REFLECTION)
        ));

        assertThrows(AssessmentException.class, () -> service(prompt -> review).score(SNAPSHOT));
    }

    private EvidenceScoringService service(AssessmentModelPort port) {
        return new EvidenceScoringService(
                port,
                new ScoreRubricService(),
                Clock.fixed(Instant.parse("2026-09-23T09:00:00Z"), ZoneOffset.UTC));
    }

    private CandidateReview review(List<CandidateReview.CandidateDimension> dimensions) {
        return new CandidateReview(
                "test-model-v1", "回答包含可核对的行动与取舍。", dimensions,
                List.of("补充验证后的反思"), "说明方案何时会失效");
    }

    private CandidateReview.CandidateDimension evidenced(
            AssessmentDimension dimension, int score, String quote) {
        return new CandidateReview.CandidateDimension(dimension, score, quote, "依据该段连续原文评分");
    }

    private CandidateReview.CandidateDimension uncovered(AssessmentDimension dimension) {
        return new CandidateReview.CandidateDimension(dimension, null, "", "回答原文没有覆盖该维度");
    }
}
