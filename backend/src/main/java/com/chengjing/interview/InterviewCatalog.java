package com.chengjing.interview;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Owner: member C (interview module).
 *
 * Built-in stage decks so a candidate can open a room before member B's 11-case catalog (B01) is
 * merged. The case text here is fictional teaching material, never a real resume, and it is clearly
 * labelled as a simulated case in the room. A later B01 wiring only needs to pass a case id into
 * {@code create} and read the material from the preparation module instead of this catalog.
 */
public final class InterviewCatalog {

    /** Roles accepted by {@code POST /api/v1/interviews}. */
    public static final Set<String> ROLES = new LinkedHashSet<>(List.of(
            "后端工程师", "前端工程师", "算法工程师", "测试工程师", "产品经理",
            "数据分析师", "运营", "项目管理", "解决方案顾问", "人力资源", "通用岗位"));

    public static final List<String> LEVELS = List.of("校招 / 初级", "中级", "高级");

    public static final Set<Integer> DURATIONS_MINUTES = new LinkedHashSet<>(List.of(15, 30, 45));

    private static final Map<String, InterviewStage> ROLE_CASES = Map.ofEntries(
            Map.entry("后端工程师", new InterviewStage("岗位案例",
                    "线上活动开始后，商品详情接口的 P99 从 180ms 涨到 2.8s，错误率升到 6%。你会先验证哪个假设，按什么顺序排查？",
                    "模拟案例，非真实事故：QPS 由 2,000 升到 12,000，应用 CPU 48%，数据库 CPU 35%，缓存命中率从 92% 掉到 41%。")),
            Map.entry("前端工程师", new InterviewStage("岗位案例",
                    "首屏在低端机型上要 4.2 秒才能交互，你会怎么定位瓶颈并安排改动顺序？",
                    "模拟案例：首包 1.8MB，主线程长任务 1.4s，图片未压缩，列表页一次渲染 2,000 行。")),
            Map.entry("算法工程师", new InterviewStage("岗位案例",
                    "离线评估提升了 3 个点，但线上指标没有变化。你会如何判断问题出在数据、特征还是评估方式？",
                    "模拟案例：训练集与线上分布差异集中在新增渠道，特征在线上有 12% 缺失，评估集来自历史抽样。")),
            Map.entry("测试工程师", new InterviewStage("岗位案例",
                    "一个只在生产偶发的支付失败问题，你会怎样设计复现路径和验证方案？",
                    "模拟案例：失败率 0.4%，集中在特定渠道与特定手机型号，日志只记录了错误码，没有请求上下文。")),
            Map.entry("产品经理", new InterviewStage("岗位案例",
                    "距离上线还有两天，销售希望临时加一个模块。你如何确定首版范围并向双方解释取舍？",
                    "模拟案例：2 名前端、2 名后端；注册简化需 2 人日，推荐模块需 6 人日；当前漏斗为到达 10,000、注册 3,000、下单 600。")),
            Map.entry("数据分析师", new InterviewStage("岗位案例",
                    "本周曝光翻倍、点击增加，但付费反而下降。你会怎么拆解并确定最先调查的环节？",
                    "模拟案例：曝光 100,000→200,000，点击 5,000→8,000，付费 500→480，支出 10,000→18,000，产品与规则未变。")),
            Map.entry("运营", new InterviewStage("岗位案例",
                    "活动很热闹，转化却下降了。你会先看哪些数据，并如何设计一个小规模实验？",
                    "模拟案例：新增渠道贡献 60% 点击但只贡献 20% 付费；两个渠道落地页相同，新渠道用户认知更少。")),
            Map.entry("项目管理", new InterviewStage("岗位案例",
                    "一个关键依赖被外部团队延期两周。你会如何调整计划并同步风险？",
                    "模拟案例：里程碑只剩 3 周，2 个子任务在关键路径上，团队已连续加班两周。")),
            Map.entry("解决方案顾问", new InterviewStage("岗位案例",
                    "客户提出的需求超出当前产品能力。你会怎样澄清真实目标并给出可落地的替代方案？",
                    "模拟案例：客户要求 1 周内完成定制集成，标准接口已支持 80% 场景，剩余部分需要排期评估。")),
            Map.entry("人力资源", new InterviewStage("岗位案例",
                    "业务部门抱怨招聘周期太长。你会如何定位瓶颈并提出可衡量的改进？",
                    "模拟案例：从投递到 offer 平均 32 天，简历初筛占 9 天，业务面试排期等待占 12 天。")),
            Map.entry("通用岗位", new InterviewStage("岗位案例",
                    "请讲一个你需要在信息不完整时做判断的经历，你依据什么决策，后来又怎么验证？",
                    "可以结合实习、课程、社团或个人的项目，说明你本人做了什么。")));

    private InterviewCatalog() {
    }

    /**
     * Builds the stage deck for a room. 15/30/45 minutes differ in how much of the deck is used, so
     * the target duration is visible to the candidate from the first screen.
     */
    public static List<InterviewStage> deck(String role, int minutes) {
        List<InterviewStage> deck = new ArrayList<>();
        deck.add(new InterviewStage("开场",
                "请简单介绍自己，重点说说你与这个岗位最相关的经历。", ""));
        deck.add(new InterviewStage("项目深挖",
                "请选一个你亲自参与的项目，谈谈你的职责、关键决策，以及如何验证结果。",
                "没有工作经历也可以使用实习、课程或个人项目。"));
        deck.add(ROLE_CASES.getOrDefault(role, ROLE_CASES.get("通用岗位")));
        if (minutes >= 30) {
            deck.add(new InterviewStage("协作与分歧",
                    "请讲一次意见不一致的经历，你如何处理，结果怎么样？", ""));
        }
        if (minutes >= 45) {
            deck.add(new InterviewStage("方案复盘",
                    "回看刚才的案例，你的方案在哪些条件下会失效？", ""));
        }
        deck.add(new InterviewStage("反问环节",
                "面试接近尾声，你有什么希望了解的？你可以提问，也可以说没有问题。",
                "这是模拟面试，没有真实雇主信息；不承诺薪酬或录用。"));
        return List.copyOf(deck);
    }
}
