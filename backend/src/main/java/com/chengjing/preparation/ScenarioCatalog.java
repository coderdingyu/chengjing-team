package com.chengjing.preparation;

import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** Public, authored simulations; never candidate resume data. */
@Service
public class ScenarioCatalog {
    public record Scenario(String id, String role, String title, String situation, String goal, boolean simulated) {}

    private static Scenario scenario(String id, String role, String title, String situation, String goal) {
        return new Scenario(id, role, title, situation, goal, true);
    }

    private final List<Scenario> scenarios = List.of(
        scenario("backend-incident", "后端工程师", "活动接口延迟升高", "模拟活动流量增长六倍，缓存命中率从90%降至40%，数据库连接等待增加，发布需要20分钟。", "给出排查顺序、可回滚的止损措施，并验证价格正确性。"),
        scenario("frontend-checkout", "前端工程师", "移动端支付页卡顿", "模拟支付页首屏耗时4.8秒，下载1.6MB脚本，低端安卓占45%，支付接口耗时正常。", "区分网络与主线程瓶颈，设计性能优化和防重复提交验证。"),
        scenario("product-launch", "产品经理", "上线前范围取舍", "模拟团队距上线两天，注册简化需2人日、优惠券需3人日、推荐模块需6人日，目标是提高首单转化。", "确定最小范围，解释资源取舍，定义转化指标与回滚条件。"),
        scenario("operations-campaign", "运营", "流量增长但付费下降", "模拟活动曝光翻倍，付费人数从500降至480，新增渠道贡献60%的点击但仅20%的付费。", "拆解渠道漏斗，提出可验证假设，兼顾获客成本和退款率。"),
        scenario("qa-release", "测试工程师", "两小时发布窗口", "模拟结算逻辑变更，完整回归需6小时但仅余2小时，优惠券过期与支付并发时偶现金额差异。", "按风险安排测试，构造最小复现并提出发布门槛。"),
        scenario("data-experiment", "数据分析师", "实验结论可靠吗", "模拟实验两组各1000人，转化分别120和100人，新用户比例分别70%和40%，实验只运行三天。", "检查分组偏差和统计不确定性，定义主指标、护栏和观察周期。"),
        scenario("design-accessibility", "体验设计师", "改版后的可用性", "模拟产品移除图标文字后，满意度提升但首次任务完成率从65%降至48%，研发资源仅一周。", "设计用户研究，兼顾可访问性，并用任务成功率检验方案。"),
        scenario("customer-renewal", "客户成功", "客户续约风险", "模拟客户30天后续约，活跃席位下降40%，采购要求降价，使用团队反映报表难用且数据尚未接通。", "厘清各方诉求，制定可兑现的短期方案和成功标准。"),
        scenario("project-dependency", "项目经理", "跨团队依赖阻塞", "模拟三个团队分别等待接口、需求和环境，距发布两周，审计字段不可取消，外部依赖需五天。", "重建关键路径，明确负责人，组织范围与发布风险决策。"),
        scenario("ai-evaluation", "AI应用工程师", "演示与实际效果差距", "模拟客服助手演示正确率95%，上线后出现错误退货承诺，P95延迟12秒，成本超预算两倍。", "建立代表性评测，区分格式与事实正确性，制定权限和灰度门槛。"),
        scenario("graduate-team", "校招通用", "第一次主导小项目", "模拟四人小组两周制作校园报名工具，一人中途退出，去重功能有缺陷，展示日期不变。", "明确目标与个人行动，调整范围，诚实复盘贡献和验证不足。")
    );

    public List<String> roles() { return scenarios.stream().map(Scenario::role).distinct().toList(); }
    public List<Scenario> list(String role) {
        return scenarios.stream().filter(s -> role == null || role.isBlank() || s.role().equals(role.strip())).toList();
    }
    public Optional<Scenario> find(String id) { return scenarios.stream().filter(s -> s.id().equals(id)).findFirst(); }
}
