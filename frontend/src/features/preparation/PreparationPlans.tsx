import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { api } from "../../api";
import "./plans.css";

type PlanNode = { id: string; kind: string; title: string; prompt: string; status: string; content: string };
type Plan = {
  id: string;
  role: string;
  title: string;
  goal: string;
  scenarioId: string | null;
  jd: string | null;
  status: string;
  nodes: PlanNode[];
  updatedAt: string;
};
type Scenario = { id: string; role: string; title: string; goal: string };

const STATUS_LABEL: Record<string, string> = {
  PLANNING: "准备中",
  READY: "可练习",
  ARCHIVED: "已归档",
};

// B01 的岗位情境目录合并前，岗位仍可手填，情境下拉留空。
const FALLBACK_ROLES = [
  "后端工程师",
  "前端工程师",
  "产品经理",
  "运营",
  "测试工程师",
  "数据分析师",
  "体验设计师",
  "客户成功",
  "项目经理",
  "AI应用工程师",
  "校招通用",
];

export default function PreparationPlans() {
  const [plans, setPlans] = useState<Plan[]>([]);
  const [roles, setRoles] = useState<string[]>(FALLBACK_ROLES);
  const [scenarios, setScenarios] = useState<Scenario[]>([]);
  const [scenarioNote, setScenarioNote] = useState("");
  const [role, setRole] = useState(FALLBACK_ROLES[0]);
  const [scenarioId, setScenarioId] = useState("");
  const [title, setTitle] = useState("");
  const [goal, setGoal] = useState("");
  const [jd, setJd] = useState("");
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [busy, setBusy] = useState(false);
  const [loading, setLoading] = useState(true);
  const [retry, setRetry] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setError("");
    Promise.all([
      api<string[]>("/preparation/roles", { signal: controller.signal }).catch(() => FALLBACK_ROLES),
      api<Plan[]>("/preparation/plans", { signal: controller.signal }),
    ])
      .then(([nextRoles, nextPlans]) => {
        if (controller.signal.aborted) return;
        setRoles(nextRoles.length > 0 ? nextRoles : FALLBACK_ROLES);
        setPlans(nextPlans);
      })
      .catch((e: Error) => {
        if (!controller.signal.aborted) setError(e.message);
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [retry]);

  useEffect(() => {
    if (!role) {
      setScenarios([]);
      return;
    }
    const controller = new AbortController();
    setScenarioNote("");
    api<Scenario[]>(`/preparation/scenarios?role=${encodeURIComponent(role)}`, { signal: controller.signal })
      .then((next) => {
        if (controller.signal.aborted) return;
        setScenarios(next);
        setScenarioId((current) => (next.some((s) => s.id === current) ? current : ""));
      })
      .catch(() => {
        if (controller.signal.aborted) return;
        setScenarios([]);
        setScenarioId("");
        setScenarioNote("岗位情境目录尚未接入，可先不关联情境。");
      });
    return () => controller.abort();
  }, [role]);

  async function create(event: React.FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError("");
    setNotice("");
    try {
      const created = await api<Plan>("/preparation/plans", {
        method: "POST",
        body: JSON.stringify({ role, title, goal, scenarioId: scenarioId || null, jd: jd || null }),
      });
      setPlans((current) => [created, ...current]);
      setTitle("");
      setGoal("");
      setJd("");
      setNotice("计划已创建，只有你自己的账号能看到它。");
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }

  async function changeStatus(plan: Plan, status: string) {
    setBusy(true);
    setError("");
    setNotice("");
    try {
      const updated = await api<Plan>(`/preparation/plans/${plan.id}`, {
        method: "PATCH",
        body: JSON.stringify({ status }),
      });
      setPlans((current) => current.map((item) => (item.id === updated.id ? updated : item)));
      setNotice(`「${updated.title}」已更新为${STATUS_LABEL[updated.status] ?? updated.status}。`);
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }

  async function remove(plan: Plan) {
    setBusy(true);
    setError("");
    setNotice("");
    try {
      await api<{ deletedId: string }>(`/preparation/plans/${plan.id}`, { method: "DELETE" });
      setPlans((current) => current.filter((item) => item.id !== plan.id));
      setNotice(`已删除「${plan.title}」。`);
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="preparation">
      <Link to="/" className="back">← 返回工作台</Link>
      <span className="eyebrow">面试准备 / 岗位准备计划</span>
      <h1>先定岗位，再定准备目标</h1>
      <p>计划只保存你写下的目标与岗位要求，列表里永远只有你自己的计划。</p>

      <form className="plan-form" onSubmit={create}>
        <label htmlFor="plan-role">目标岗位</label>
        <input
          id="plan-role"
          list="plan-role-options"
          value={role}
          onChange={(e) => setRole(e.target.value)}
          placeholder="例如：后端工程师"
          required
        />
        <datalist id="plan-role-options">
          {roles.map((item) => (
            <option key={item} value={item} />
          ))}
        </datalist>
        <label htmlFor="plan-scenario">关联模拟情境（可选）</label>
        <select
          id="plan-scenario"
          value={scenarioId}
          onChange={(e) => setScenarioId(e.target.value)}
          disabled={scenarios.length === 0}
        >
          <option value="">不关联情境</option>
          {scenarios.map((item) => (
            <option key={item.id} value={item.id}>{item.title}</option>
          ))}
        </select>
        {scenarioNote && <p className="footnote">{scenarioNote}</p>}
        <label htmlFor="plan-title">计划名称</label>
        <input
          id="plan-title"
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          placeholder="例如：秋招后端准备"
          required
        />
        <label htmlFor="plan-goal">准备目标</label>
        <textarea
          id="plan-goal"
          rows={3}
          value={goal}
          onChange={(e) => setGoal(e.target.value)}
          placeholder="这次准备要解决什么，例如：把三次项目经历讲出取舍与验证"
          required
        />
        <label htmlFor="plan-jd">岗位要求（可选）</label>
        <textarea
          id="plan-jd"
          rows={3}
          value={jd}
          onChange={(e) => setJd(e.target.value)}
          placeholder="粘贴公开的岗位要求，不要写个人信息"
        />
        <button type="submit" disabled={busy}>创建计划</button>
      </form>

      {loading && <p role="status">正在加载计划…</p>}
      {error && (
        <div role="alert" className="plan-alert">
          <p>{error}</p>
          <button onClick={() => setRetry((n) => n + 1)}>重新加载</button>
        </div>
      )}
      {notice && <p role="status">{notice}</p>}

      {!loading && plans.length === 0 && <p>还没有计划。选一个岗位，写下这次准备的目标。</p>}
      <ul className="plan-list">
        {plans.map((plan) => (
          <li key={plan.id}>
            <div className="plan-head">
              <strong>{plan.title}</strong>
              <span className="plan-status">{STATUS_LABEL[plan.status] ?? plan.status}</span>
            </div>
            <p>{plan.role}{plan.scenarioId ? ` · 情境 ${plan.scenarioId}` : ""}</p>
            <p>{plan.goal}</p>
            <div className="plan-actions">
              <button disabled={busy} onClick={() => changeStatus(plan, "READY")}>标记为可练习</button>
              <button disabled={busy} onClick={() => changeStatus(plan, "ARCHIVED")}>归档</button>
              <button disabled={busy} onClick={() => remove(plan)}>删除</button>
            </div>
          </li>
        ))}
      </ul>
      <p className="footnote">只有你自己的账号能看到它；别人的计划会返回「属于其他账号，不能查看或修改」。</p>
    </section>
  );
}
