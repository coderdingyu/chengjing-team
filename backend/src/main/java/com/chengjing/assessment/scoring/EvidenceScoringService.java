package com.chengjing.assessment.scoring;

import com.chengjing.assessment.AssessmentException;
import com.chengjing.assessment.rubric.AssessmentDimension;
import com.chengjing.assessment.rubric.CoverageStatus;
import com.chengjing.assessment.rubric.ScoreRubric;
import com.chengjing.assessment.rubric.ScoreRubricService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;

@Service
public class EvidenceScoringService {
    private final AssessmentModelPort modelPort;
    private final ScoreRubricService rubricService;
    private final Clock clock;

    @Autowired
    public EvidenceScoringService(AssessmentModelPort modelPort, ScoreRubricService rubricService) {
        this(modelPort, rubricService, Clock.systemUTC());
    }

    EvidenceScoringService(AssessmentModelPort modelPort, ScoreRubricService rubricService, Clock clock) {
        this.modelPort = modelPort;
        this.rubricService = rubricService;
        this.clock = clock;
    }

    public EvidenceScore score(AnswerSnapshot answer) {
        validateAnswer(answer);
        ScoreRubric rubric = rubricService.current();
        CandidateReview candidate = modelPort.score(new ScoringPrompt(
                answer.userId(), answer.roleKey(), answer.questionText(), answer.answerText(), rubric));
        return validateCandidate(answer, candidate, rubric.version(), Instant.now(clock));
    }

    EvidenceScore validateCandidate(
            AnswerSnapshot answer,
            CandidateReview candidate,
            String rubricVersion,
            Instant generatedAt
    ) {
        if (candidate == null || candidate.dimensions() == null
                || candidate.dimensions().size() != AssessmentDimension.values().length) {
            throw invalidModelOutput("模型必须返回四个且仅四个评分维度");
        }
        requireText(candidate.modelVersion(), "模型版本不能为空");
        requireText(candidate.summary(), "评分摘要不能为空");
        requireText(candidate.nextAction(), "下一步建议不能为空");
        if (candidate.gaps() == null || candidate.gaps().size() > 3
                || candidate.gaps().stream().anyMatch(gap -> gap == null || gap.isBlank())) {
            throw invalidModelOutput("差距列表最多三项且不能为空");
        }

        EnumSet<AssessmentDimension> seen = EnumSet.noneOf(AssessmentDimension.class);
        List<EvidenceScore.DimensionScore> dimensions = new ArrayList<>();
        int points = 0;
        int covered = 0;
        for (CandidateReview.CandidateDimension row : candidate.dimensions()) {
            if (row == null || row.dimension() == null || !seen.add(row.dimension())) {
                throw invalidModelOutput("评分维度缺失或重复");
            }
            requireText(row.reason(), "每个维度都必须说明理由");
            String quote = Objects.toString(row.quote(), "").trim();
            if (row.score() == null) {
                if (!quote.isEmpty()) {
                    throw invalidModelOutput("未覆盖维度不得附带无法计分的引文");
                }
                dimensions.add(new EvidenceScore.DimensionScore(
                        row.dimension(), row.dimension().label(), CoverageStatus.UNCOVERED,
                        null, "", -1, -1, row.reason().trim()));
                continue;
            }
            if (row.score() < 0 || row.score() > 4 || quote.isEmpty()) {
                throw invalidModelOutput("有分数的维度必须是 0～4 整数并带有原话证据");
            }
            int start = answer.answerText().indexOf(quote);
            if (start < 0) {
                throw invalidModelOutput("模型引文不在该回答原文中");
            }
            covered++;
            points += row.score();
            dimensions.add(new EvidenceScore.DimensionScore(
                    row.dimension(), row.dimension().label(), CoverageStatus.EVIDENCED,
                    row.score(), quote, start, start + quote.length(), row.reason().trim()));
        }
        if (seen.size() != AssessmentDimension.values().length) {
            throw invalidModelOutput("评分维度不完整");
        }
        Integer overall = covered == AssessmentDimension.values().length
                ? Math.round(points * 100f / (AssessmentDimension.values().length * 4))
                : null;
        return new EvidenceScore(
                answer.answerId(), answer.questionId(), rubricVersion, candidate.modelVersion().trim(),
                generatedAt, overall, covered, candidate.summary().trim(), List.copyOf(dimensions),
                List.copyOf(candidate.gaps()), candidate.nextAction().trim());
    }

    private void validateAnswer(AnswerSnapshot answer) {
        if (answer == null) {
            throw AssessmentException.badRequest("回答记录不能为空");
        }
        requireText(answer.userId(), "回答缺少用户归属");
        requireText(answer.answerId(), "回答 ID 不能为空");
        requireText(answer.questionId(), "问题 ID 不能为空");
        requireText(answer.questionText(), "问题文本不能为空");
        requireText(answer.answerText(), "空回答不能评分");
    }

    private void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw invalidModelOutput(message);
        }
    }

    private AssessmentException invalidModelOutput(String message) {
        return AssessmentException.badRequest("评分结果无效：" + message);
    }
}
