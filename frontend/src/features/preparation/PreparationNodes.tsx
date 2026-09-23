import { useCallback, useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { api } from "../../api";
import "./nodes.css";

type PlanNode = {
  id: string;
  kind: string;
  title: string;
  prompt: string;
  status: string;
  content: string;
  updatedAt: string;
};
type NodeView = { planId: string; role: string; node: PlanNode };
type Plan = { id: string; role: string; title: string; goal: string; status: string };

const KIND_LABEL: Record<string, string> = {
  BACKGROUND: "项目背景",
  ACTION: "个人行动",
  TRADEOFF: "取舍判断",
  RESULT: "结果验证",
};

const STATUS_LABEL: Record<string, string> = {
  TODO: "待填写",
  DRAFTING: "草稿中",
  DONE: "已讲清",
};

/** 简历深挖节点：给一份计划生成四类节点，并逐条保存状态与内容。 */
export default function PreparationNodes() {
  const { planId = "" } = useParams();
  const [plan, setPlan] = useState<Plan | null>(null);
  const [nodes, setNodes] = useState<NodeView[]>([]);
  const [drafts, setDrafts] = useState<Record<string, string>>({});
  const [focus, setFocus] = useState("");
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
      api<Plan>(`/preparation/plans/${planId}`, { signal: controller.signal }),
      api<NodeView[]>(`/preparation/plans/${planId}/nodes`, { signal: controller.signal }),
    ])
      .then(([nextPlan, nextNodes]) => {
        if (controller.signal.aborted) return;
        setPlan(nextPlan);
        setNodes(nextNodes);
        setDrafts(
          Object.fromEntries(nextNodes.map((item) => [item.node.id, item.node.content ?? ""])),
        );
      })
      .catch((e: Error) => {
        if (!controller.signal.aborted) setError(e.message);
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [planId, retry]);

  const generate = useCallback(
    async (replace: boolean) => {
      setBusy(true);
      setError("");
      setNotice("");
      try {
        const next = await api<NodeView[]>(`/preparation/plans/${planId}/nodes`, {
          method: "POST",
          body: JSON.stringify({ focus: focus || null, replace }),
        });
        setNodes(next);
        setDrafts(Object.fromEntries(next.map((item) => [item.node.id, item.node.content ?? ""])));
        setNotice(
          replace
            ? "节点已重做，原来的内容已清空。"
            : "已按这个岗位补齐节点，已经写好的内容保持不动。",
        );
      } catch (e) {
        setError((e as Error).message);
      } finally {
        setBusy(false);
      }
    },
    [planId, focus],
  );

  async function save(node: PlanNode, status?: string) {
    setBusy(true);
    setError("");
    setNotice("");
    try {
      const updated = await api<NodeView>(`/preparation/plans/${planId}/nodes/${node.id}`, {
        method: "PATCH",
        body: JSON.stringify({
          status: status ?? node.status,
          content: drafts[node.id] ?? "",
        }),
      });
      setNodes((current) =>
        current.map((item) => (item.node.id === node.id ? updated : item)),
      );
      setDrafts((current) => ({ ...current, [node.id]: updated.node.content ?? "" }));
      setNotice(`「${node.title}」已保存为${STATUS_LABEL[updated.node.status] ?? updated.node.status}。`);
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="preparation">
      <Link to="/modules/preparation/plans" className="back">← 返回准备计划</Link>
      <span className="eyebrow">面试准备 / 简历深挖节点</span>
      <h1>{plan ? `${plan.title} · 深挖地图` : "简历深挖节点"}</h1>
      <p>
        把一段经历拆成项目背景、个人行动、取舍判断、结果验证四段，每段单独保存状态。
        节点只保存你自己写下的事实，不会写进任何公开案例。
      </p>

      <div className="node-toolbar">
        <label htmlFor="node-focus">这段经历的方向（可选）</label>
        <input
          id="node-focus"
          value={focus}
          onChange={(e) => setFocus(e.target.value)}
          placeholder="例如：支付对账重构"
        />
        <button disabled={busy} onClick={() => generate(false)}>生成节点</button>
        <button disabled={busy || nodes.length === 0} onClick={() => generate(true)}>重做节点</button>
      </div>

      {loading && <p role="status">正在加载节点…</p>}
      {error && (
        <div role="alert" className="plan-alert">
          <p>{error}</p>
          <button onClick={() => setRetry((n) => n + 1)}>重新加载</button>
        </div>
      )}
      {notice && <p role="status">{notice}</p>}

      {!loading && nodes.length === 0 && (
        <p>还没有节点。填写经历方向后点「生成节点」，会得到四类深挖节点。</p>
      )}
      <ul className="node-list">
        {nodes.map((item) => (
          <li key={item.node.id}>
            <div className="plan-head">
              <strong>
                {KIND_LABEL[item.node.kind] ?? item.node.kind}·{item.node.title}
              </strong>
              <span className="plan-status">
                {STATUS_LABEL[item.node.status] ?? item.node.status}
              </span>
            </div>
            <p className="node-prompt">{item.node.prompt}</p>
            <textarea
              rows={4}
              value={drafts[item.node.id] ?? ""}
              onChange={(e) =>
                setDrafts((current) => ({ ...current, [item.node.id]: e.target.value }))
              }
              placeholder="只写本人确认的事实；写不下去的可以留空，下次接着写。"
            />
            <div className="plan-actions">
              <button disabled={busy} onClick={() => save(item.node)}>保存草稿</button>
              <button disabled={busy} onClick={() => save(item.node, "DONE")}>标记为已讲清</button>
              <Link
                className="plan-enter"
                to={`/modules/preparation/plans/${planId}/nodes/${item.node.id}/practice`}
              >
                练这道题
              </Link>
            </div>
          </li>
        ))}
      </ul>
      <p className="footnote">
        别人访问这份计划会得到「属于其他账号，不能查看或修改」，节点与计划共用同一道归属校验。
      </p>
    </section>
  );
}
