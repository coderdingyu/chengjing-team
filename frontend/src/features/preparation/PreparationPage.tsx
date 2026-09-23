import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { api } from "../../api";
import "./preparation.css";

type Scenario = { id: string; role: string; title: string; situation: string; goal: string; simulated: boolean };

export default function PreparationPage() {
  const [roles, setRoles] = useState<string[]>([]);
  const [role, setRole] = useState("");
  const [scenarios, setScenarios] = useState<Scenario[]>([]);
  const [selected, setSelected] = useState<Scenario | null>(null);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);
  const [retry, setRetry] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    setLoading(true); setError(""); setSelected(null);
    Promise.all([
      api<string[]>("/preparation/roles", { signal: controller.signal }),
      api<Scenario[]>(`/preparation/scenarios?role=${encodeURIComponent(role)}`, { signal: controller.signal }),
    ]).then(([nextRoles, nextScenarios]) => {
      if (!controller.signal.aborted) { setRoles(nextRoles); setScenarios(nextScenarios); }
    }).catch((e: Error) => {
      if (!controller.signal.aborted) { setError(e.message); setScenarios([]); }
    }).finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [role, retry]);

  return <section className="preparation">
    <Link to="/" className="back">← 返回工作台</Link>
    <span className="eyebrow">面试准备 / 岗位情境</span>
    <h1>从岗位情境开始准备</h1>
    <p>11 类岗位的公开模拟案例。情境仅供练习，不代表你的真实经历。</p>
    <label htmlFor="preparation-role">选择岗位</label>{" "}
    <select id="preparation-role" value={role} onChange={e => setRole(e.target.value)}>
      <option value="">全部岗位</option>
      {roles.map(r => <option key={r} value={r}>{r}</option>)}
    </select>
    {loading && <p role="status">正在加载岗位情境…</p>}
    {error && <div role="alert"><p>{error}</p><button onClick={() => setRetry(n => n + 1)}>重新加载</button></div>}
    {!loading && !error && scenarios.length === 0 && <p>该岗位暂时没有情境。</p>}
    {!loading && !error && <div className="module-grid">
      {scenarios.map(s => <article className="module-card" key={s.id}>
        <span>{s.role} · 公开模拟</span><h2>{s.title}</h2><p>{s.goal}</p>
        <button aria-pressed={selected?.id === s.id} onClick={() => setSelected(s)}>查看情境与目标</button>
      </article>)}
    </div>}
    {selected && <article className="scenario-detail" aria-live="polite">
      <h2>{selected.title}</h2><p>模拟情境 · {selected.role}</p>
      <h3>情境</h3><p>{selected.situation}</p><h3>准备目标</h3><p>{selected.goal}</p>
      <button onClick={() => setSelected(null)}>收起详情</button>
    </article>}
  </section>;
}
