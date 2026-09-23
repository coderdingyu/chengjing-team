package com.chengjing.preparation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 简历深挖节点模板：按目标岗位给出一组能分别讲述、分别验证的节点。
 * 模板只包含公开的岗位场景与提问方式，不含任何真实简历内容；
 * 本人的具体事实由账号本人在节点内容里填写，因此可以安全地展示给任何账号。
 */
public final class ResumeNodeCatalog {
    public static final String KIND_BACKGROUND = "BACKGROUND";
    public static final String KIND_ACTION = "ACTION";
    public static final String KIND_TRADEOFF = "TRADEOFF";
    public static final String KIND_RESULT = "RESULT";

    /** 一个深挖节点模板：节点类型、节点名和给本人看的提问。 */
    public record NodeTemplate(String kind, String title, String prompt) {}

    private static final Map<String, String> ROLE_SCENES = new LinkedHashMap<>();

    static {
        ROLE_SCENES.put("后端", "一次线上故障排查或接口性能优化");
        ROLE_SCENES.put("前端", "一次页面性能优化或交互改版");
        ROLE_SCENES.put("产品", "一次需求取舍或版本节奏安排");
        ROLE_SCENES.put("运营", "一次活动增长或渠道投放");
        ROLE_SCENES.put("测试", "一次回归漏测或质量门禁改进");
        ROLE_SCENES.put("数据", "一次指标口径统一或归因分析");
        ROLE_SCENES.put("设计", "一次可用性测试或改版验证");
        ROLE_SCENES.put("客户", "一次续约风险或客户投诉处理");
        ROLE_SCENES.put("项目", "一次跨团队排期或风险兜底");
        ROLE_SCENES.put("AI", "一次模型效果提升或评测集建设");
    }

    private static final String DEFAULT_SCENE = "一次课程项目、实习任务或社团活动";

    private ResumeNodeCatalog() {}

    /** 岗位场景：按岗位名里的关键词匹配，认不出来时用通用场景。 */
    public static String scene(String role) {
        String value = role == null ? "" : role;
        for (Map.Entry<String, String> entry : ROLE_SCENES.entrySet()) {
            if (value.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return DEFAULT_SCENE;
    }

    /**
     * 生成四个节点：项目背景、个人行动、取舍判断、结果验证。
     * focus 是本人写下的经历方向（例如「支付对账重构」），留空时用岗位场景作例子。
     */
    public static List<NodeTemplate> forRole(String role, String focus) {
        String scene = scene(role);
        String subject = focus == null || focus.isBlank() ? scene : focus.strip();
        return List.of(
                new NodeTemplate(KIND_BACKGROUND, "项目背景",
                        "用三四句话交代「" + subject + "」的处境：这是什么任务、当时的约束是什么、为什么需要你来做。只写可以公开的事实。"),
                new NodeTemplate(KIND_ACTION, "个人行动",
                        "写清你本人做了什么：你负责的边界、具体动作与顺序，以及哪些事不是你做的。"),
                new NodeTemplate(KIND_TRADEOFF, "取舍判断",
                        "写下当时的备选方案，说明你为什么选它、放弃了什么、依据是什么。"),
                new NodeTemplate(KIND_RESULT, "结果验证",
                        "给出结果与验证方式：用什么指标或事实确认有效，哪些情况下会失效，事后你会怎么调整。"));
    }
}
