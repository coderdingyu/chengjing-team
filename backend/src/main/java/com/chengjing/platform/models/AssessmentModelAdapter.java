package com.chengjing.platform.models;

import com.chengjing.assessment.AssessmentException;
import com.chengjing.assessment.rubric.AssessmentDimension;
import com.chengjing.assessment.scoring.AssessmentModelPort;
import com.chengjing.assessment.scoring.CandidateReview;
import com.chengjing.assessment.scoring.ScoringPrompt;
import com.chengjing.platform.PlatformException;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Adapts D's evidence rubric to the same account-selected text gateway used by dialogue. */
@Component
public class AssessmentModelAdapter implements AssessmentModelPort {
    private final TextModelPort gateway;

    public AssessmentModelAdapter(TextModelPort gateway) { this.gateway = gateway; }

    @Override
    public CandidateReview score(ScoringPrompt prompt) {
        try {
            var result = gateway.completeJson(prompt.userId(), "grading",
                    "按给定四维量表逐维评分。每维给出 dimension 枚举名、0 到 4 整数 score 或 null、"
                    + "回答原文中的逐字 quote（未覆盖时空字符串）、简要 reason。没有证据必须 score=null。"
                    + "输出字段：summary, dimensions 数组, gaps 字符串数组（最多3项）, nextAction。",
                    Map.of("role", prompt.roleKey(), "question", prompt.questionText(),
                            "answer", prompt.answerText(), "rubric", prompt.rubric()));
            JsonNode json = result.content();
            if (!json.path("dimensions").isArray() || json.path("dimensions").size() != 4) throw invalid();
            List<CandidateReview.CandidateDimension> dimensions = new ArrayList<>();
            for (JsonNode row : json.path("dimensions")) {
                AssessmentDimension dimension = AssessmentDimension.valueOf(row.path("dimension").asText());
                Integer score = row.path("score").isNull() ? null : row.path("score").asInt(-1);
                dimensions.add(new CandidateReview.CandidateDimension(dimension, score,
                        row.path("quote").asText(""), row.path("reason").asText("")));
            }
            if (!json.path("gaps").isArray()) throw invalid();
            List<String> gaps = new ArrayList<>();
            for (JsonNode gap : json.path("gaps")) gaps.add(gap.asText());
            return new CandidateReview(result.model(), json.path("summary").asText(""),
                    dimensions, gaps, json.path("nextAction").asText(""));
        } catch (PlatformException e) { throw AssessmentException.unavailable(e.getMessage()); }
        catch (Exception e) { throw invalid(); }
    }

    private AssessmentException invalid() {
        return AssessmentException.unavailable("评分模型输出缺少必要字段，请重试或更换模型");
    }
}
