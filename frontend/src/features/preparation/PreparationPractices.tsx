import { useCallback, useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { api } from "../../api";
import "./practices.css";

type PracticeAnswer = {
  id: string;
  questionId: string;
  question: string;
  text: string;
  revision: number;
  answeredAt: string;
};

type Practice = {
  id: string;
  planId: string;
  nodeId: string;
  nodeTitle: string;
  questionId: string;
  question: string;
  status: string;
  statusLabel: string;
  draft: string;
  attemptCount: number;
  answers: PracticeAnswer[];
  updatedAt: string;
};

type NodeView = { planId: string; role: string; node: { id: string; kind: string; title: string; status: string } };
type Plan = { id: string; role: string; title: string; goal: string };

const KIND_LABEL: Record<string, string> = {
  BACKGROUND: "项目背景",
  ACTION: "个人行动",
  TRADEOFF: "取舍判断",
  RESULT: "结果验证",
};

/** 草稿在本机也留一份：网络或服务端出错时，输入框里的字不会被清掉。 */
function draftKey(planId: string, practiceId: string) {
  return `chengjing:practice:${planId}:${practiceId}`;
}

/** 单题专项练习：从深挖节点发起题目，写草稿、提交文字回答、反复重答。 */
export default function PreparationPractices() {
  const { planId = "", nodeId = "" } = useParams();
  const [plan, setPlan] = useState<Plan | null>(null);
  const [node, setNode] = useState<NodeView["node"] | null>(null);
  const [practice, setPractice] = useState<Practice | null>(null);
  const [draft, setDraft] = useState("");
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [busy, setBusy] = useState(false);
  const [loading, setLoading] = useState(true);
  const [retry, setRetry] = useState(0);

  // 打开发起练习：同一个节点重复进来会复用已有练习，不会每次都新建一条。
  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setError("");
    setNotice("");
    Promise.all([
      api<Plan>(`/preparation/plans/${planId}`, { signal: controller.signal }),
      api<NodeView[]>(`/preparation/plans/${planId}/nodes`, { signal: controller.signal }),
    ])
      .then(async ([nextPlan, nodes]) => {
        if (controller.signal.aborted) return;
        const found = nodes.find((item) => item.node.id === nodeId);
        if (!found) throw new Error("深挖节点不存在，请先在深挖地图里生成节点");
        setPlan(nextPlan);
        setNode(found.node);
        const created = await api<Practice>(
          `/preparation/plans/${planId}/nodes/${nodeId}/practices`,
          { method: "POST", signal: controller.signal },
        );
        if (controller.signal.aborted) return;
        setPractice(created);
        setDraft(localStorage.getItem(draftKey(planId, created.id)) ?? created.draft ?? "");
      })
      .catch((e: Error) => {
        if (!controller.signal.aborted) setError(e.message);
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [planId, nodeId, retry]);

  // 草稿写在本机，刷新或提交失败后仍然能接着写。
  const changeDraft = useCallback(
    (value: string) => {
      setDraft(value);
      if (practice) localStorage.setItem(draftKey(planId, practice.id), value);
    },
    [planId, practice],
  );

  async function saveDraft() {
    if (!practice) return;
    setBusy(true);
    setError("");
    setNotice("");
    try {
      const updated = await api<Practice>(`/preparation/practices/${practice.id}`, {
        method: "PATCH",
        body: JSON.stringify({ draft }),
      });
      setPractice(updated);
      setNotice("草稿已保存，可以下次接着写。");
    } catch (e) {
      // 保存失败时本机草稿原样保留，输入框不清空。
      setError(`${(e as Error).message}（草稿已留在本机，可修改后重试）`);
    } finally {
      setBusy(false);
    }
  }

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    if (!practice) return;
    setBusy(true);
    setError("");
    setNotice("");
    const requestId = `${practice.id}-${practice.attemptCount + 1}`;
    try {
      const updated = await api<Practice>(`/preparation/practices/${practice.id}/answers`, {
        method: "POST",
        body: JSON.stringify({ text: draft, requestId }),
      });
      setPractice(updated);
      setDraft("");
      localStorage.removeItem(draftKey(planId, practice.id));
      setNotice(`已保存第 ${updated.attemptCount} 次回答，草稿已清空。`);
    } catch (e) {
      setError(`${(e as Error).message}（草稿已保留，可以改完再提交）`);
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="preparation">
      <Link to={`/modules/preparation/plans/${planId}/nodes`} className="back">
        ← 返回深挖地图
      </Link>
      <span className="eyebrow">面试准备 / 单题专项练习</span>
      <h1>{node ? `${KIND_LABEL[node.kind] ?? node.kind} · ${node.title}` : "单题专项练习"}</h1>
      <p>
        {plan ? `计划：${plan.title}（${plan.role}）` : "正在读取计划…"}
      </p>

      {loading && <p role="status">正在准备这道题…</p>}
      {error && (
        <div role="alert" className="plan-alert">
          <p>{error}</p>
          <button onClick={() => setRetry((n) => n + 1)}>重新加载</button>
        </div>
      )}
      {notice && <p role="status">{notice}</p>}

      {practice && (
        <>
          <div className="practice-card">
            <div className="plan-head">
              <strong>题目</strong>
              <span className="plan-status">{practice.statusLabel}</span>
            </div>
            <p className="practice-question">{practice.question}</p>
            <p className="footnote">
              已提交 {practice.attemptCount} 次；每次回答都会按顺序保留，方便对照自己前后两次的说法。
            </p>
          </div>

          <form className="plan-form practice-form" onSubmit={submit}>
            <label htmlFor="practice-draft">你的回答（也是草稿）</label>
            <textarea
              id="practice-draft"
              rows={8}
              value={draft}
              onChange={(e) => changeDraft(e.target.value)}
              placeholder="先写下来再改：交代处境、你本人做了什么、当时的取舍、结果怎么验证。只写可以公开的事实。"
            />
            <p className="footnote">
              草稿会留在本机，提交失败或被拒绝时不会丢；提交成功后草稿清空，回答留在下面。
            </p>
            <div className="plan-actions">
              <button type="submit" disabled={busy}>提交这次回答</button>
              <button type="button" disabled={busy} onClick={saveDraft}>只保存草稿</button>
            </div>
          </form>

          {practice.answers.length === 0 && <p>还没有提交过回答。写完之后点「提交这次回答」。</p>}
          <ol className="practice-history">
            {practice.answers.map((answer) => (
              <li key={answer.id}>
                <div className="plan-head">
                  <strong>第 {answer.revision} 次回答</strong>
                  <span className="footnote">{new Date(answer.answeredAt).toLocaleString()}</span>
                </div>
                <p>{answer.text}</p>
              </li>
            ))}
          </ol>
          <p className="footnote">
            这条练习只属于你自己的账号，别人打开会收到「属于其他账号，不能查看或修改」。
            这里保存的是文字回答，不是评分；评分由 D02 的评分接口单独给出。
          </p>
        </>
      )}
    </section>
  );
}
