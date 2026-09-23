package com.chengjing.assessment.rubric;

import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Service
public class ScoreRubricService {
    public static final String VERSION = "assessment-4d-2026-09-23.v1";

    private static final List<ScoreRubric.ScoreAnchor> SCALE = List.of(
            new ScoreRubric.ScoreAnchor(0, "原话中存在可指出的明确错误；信息缺失不得记 0 分"),
            new ScoreRubric.ScoreAnchor(1, "与题目相关但笼统，原话不足以确认实际行动或依据"),
            new ScoreRubric.ScoreAnchor(2, "有具体事实或步骤，但关键推理、边界或验证仍不完整"),
            new ScoreRubric.ScoreAnchor(3, "行动具体，依据符合题目约束，并给出可执行验证"),
            new ScoreRubric.ScoreAnchor(4, "在 3 分基础上比较替代方案、说明失效边界并完成验证")
    );

    private static final List<String> EVIDENCE_RULES = List.of(
            "只使用本次文字回答中的连续原文作为证据",
            "没有原文证据的维度标记为未覆盖，分数留空，不能填 0",
            "区分计划、假设、团队结果与本人已经完成并验证的事实",
            "不按字数、术语数量、口音、声音、外貌、表情、性别或年龄评分",
            "评分是训练反馈，不代表录用概率或真人面试官结论"
    );

    public ScoreRubric current() {
        List<ScoreRubric.DimensionRule> dimensions = Arrays.stream(AssessmentDimension.values())
                .map(dimension -> new ScoreRubric.DimensionRule(
                        dimension.name(), dimension.label(), dimension.description()))
                .toList();
        return new ScoreRubric(VERSION, dimensions, SCALE, EVIDENCE_RULES);
    }
}
