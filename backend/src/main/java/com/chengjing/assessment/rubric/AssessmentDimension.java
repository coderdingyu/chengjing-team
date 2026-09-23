package com.chengjing.assessment.rubric;

public enum AssessmentDimension {
    QUESTION_GOAL("问题目标", "是否回应题目目标、场景与关键约束"),
    PERSONAL_ACTION("个人行动", "是否说明本人实际采取的具体行动与职责边界"),
    ANALYSIS_TRADEOFF("分析取舍", "是否给出判断依据、替代方案与取舍"),
    VALIDATION_REFLECTION("验证反思", "是否说明结果验证、失效边界与后续反思");

    private final String label;
    private final String description;

    AssessmentDimension(String label, String description) {
        this.label = label;
        this.description = description;
    }

    public String label() { return label; }
    public String description() { return description; }
}
