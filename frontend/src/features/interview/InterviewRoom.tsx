import { useCallback, useEffect, useRef, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { interviewApi } from "./interviewApi";
import type {
  InterviewAnswerView,
  InterviewRoomView,
  InterviewTurnResultView,
  InterviewTurnView,
} from "./types";
import { gradeLabel, newRequestId, statusLabel } from "./types";
import "./interview.css";

/**
 * Owner: member C. C01 — reopen a room that already exists; C02 — text answers with an ordered
 * transcript.
 *
 * The room is loaded from the server by id, so a browser refresh lands on the same room instead of
 * creating a new one, and a room owned by another account answers with "不存在".
 *
 * A submission carries a `requestId` that is minted once per distinct draft and reused on a retry,
 * so clicking "提交" twice (or a network resend) records the answer exactly once.
 */

/** The submit id is bound to the text it was minted for; the same text reuses the same id. */
function useSubmissionId() {
  const ref = useRef<{ text: string; id: string }>({ text: "", id: "" });
  return useCallback((text: string) => {
    if (ref.current.id === "" || ref.current.text !== text) {
      ref.current = { text, id: newRequestId() };
    }
    return ref.current.id;
  }, []);
}

export function InterviewRoom() {
  const { id = "" } = useParams();
  const [room, setRoom] = useState<InterviewRoomView>();
  const [loadError, setLoadError] = useState("");
  const [draft, setDraft] = useState("");
  const [sendError, setSendError] = useState("");
  const [sending, setSending] = useState(false);
  const [dialogueSource, setDialogueSource] = useState("");
  const requestIdFor = useSubmissionId();

  useEffect(() => {
    let cancelled = false;
    interviewApi
      .get<InterviewRoomView>(`/interviews/${id}`)
      .then((loaded) => {
        if (!cancelled) {
          setRoom(loaded);
          setLoadError("");
        }
      })
      .catch((problem: Error) => {
        if (!cancelled) setLoadError(problem.message);
      });
    return () => {
      cancelled = true;
    };
  }, [id]);

  const submit = useCallback(async () => {
    if (!room) return;
    const text = draft.trim();
    if (!text || sending) return;

    // One id per distinct answer: a repeat submit of the same text replays instead of duplicating.
    const requestId = requestIdFor(text);
    setSending(true);
    setSendError("");
    try {
      const result = await interviewApi.post<InterviewTurnResultView>(`/interviews/${room.id}/turns`, {
        requestId,
        questionId: room.questionId,
        text,
      });
      setRoom((current) =>
        current
          ? {
              ...current,
              turns: result.transcript,
              answers: result.answers,
              stageIndex: result.stageIndex,
              totalStages: result.totalStages,
              status: result.status,
            }
          : current,
      );
      setDialogueSource(result.dialogueSource);
      setDraft("");
    } catch (problem) {
      setSendError(problem instanceof Error ? problem.message : "提交失败，请重试");
    } finally {
      setSending(false);
    }
  }, [draft, requestIdFor, room, sending]);

  if (loadError) {
    return (
      <div className="iv-room">
        <Link to="/interviews" className="iv-back">
          ← 返回面试大厅
        </Link>
        <p role="alert" className="iv-error">
          {loadError}
        </p>
      </div>
    );
  }

  if (!room) {
    return (
      <div className="iv-room">
        <p className="iv-muted">正在打开面试室…</p>
      </div>
    );
  }

  const progress = Math.round(((room.stageIndex + 1) / room.totalStages) * 100);
  const answered = room.answers.length;
  const canSubmit = draft.trim().length > 0 && !sending;

  return (
    <div className="iv-room">
      <Link to="/interviews" className="iv-back">
        ← 返回面试大厅
      </Link>

      <header className="iv-room-heading">
        <div>
          <span className="eyebrow">
            {room.role} · {room.level}
          </span>
          <h1>{room.title}</h1>
        </div>
        <div className="iv-room-meta">
          <span className={`iv-badge iv-badge-${room.status.toLowerCase()}`}>
            {statusLabel(room.status)}
          </span>
          <span>已作答 {answered} 题</span>
          <span>目标时长 {room.minutes} 分钟</span>
        </div>
      </header>

      <section className="iv-progress" aria-label="环节进度">
        <div className="iv-progress-track">
          <div className="iv-progress-fill" style={{ width: `${progress}%` }} />
        </div>
        <p>
          第 {room.stageIndex + 1} / {room.totalStages} 环节 · {room.stage.title}
        </p>
      </section>

      <div className="iv-room-layout">
        <section className="iv-stage-card">
          <span className="iv-stage-label">当前环节 · {room.stage.title}</span>
          <p className="iv-question">{room.stage.prompt}</p>
          {room.stage.material && (
            <details className="iv-material">
              <summary>本环节材料</summary>
              <p>{room.stage.material}</p>
            </details>
          )}
        </section>

        <section className="iv-panel iv-conversation">
          <h2>对话记录</h2>
          {room.turns.length === 0 ? (
            <p className="iv-muted">
              还没有记录。在下方写下你的回答并提交，问答会按顺序出现在这里。
            </p>
          ) : (
            <ol className="iv-transcript" aria-label="问答记录">
              {room.turns.map((turn) => (
                <TranscriptLine key={turn.id} turn={turn} />
              ))}
            </ol>
          )}

          <form
            className="iv-composer"
            onSubmit={(event) => {
              event.preventDefault();
              void submit();
            }}
          >
            <label htmlFor="iv-draft">你的回答</label>
            <textarea
              id="iv-draft"
              value={draft}
              onChange={(event) => setDraft(event.target.value)}
              rows={5}
              maxLength={8000}
              placeholder="用文字写下你的回答，提交后会生成一行有顺序的问答记录。"
            />
            <div className="iv-composer-foot">
              <span className="iv-muted">{draft.trim().length} / 8000</span>
              <button type="submit" className="iv-primary" disabled={!canSubmit}>
                {sending ? "提交中…" : "提交回答"}
              </button>
            </div>
            {sendError && (
              <p role="alert" className="iv-error">
                {sendError}
              </p>
            )}
          </form>

          {dialogueSource && (
            <p className="iv-source-note">
              {dialogueSource === "LOCAL"
                ? "当前由本地确定性规则生成追问（尚未接入语音/模型网关），提交内容仅保存在本次面试房间中。"
                : "追问来自外部对话服务。"}
            </p>
          )}
        </section>
      </div>

      {room.answers.length > 0 && (
        <section className="iv-panel iv-answers">
          <h2>已保存的回答</h2>
          <ol className="iv-answer-list">
            {room.answers.map((answer, index) => (
              <AnswerItem key={answer.id} answer={answer} index={index} />
            ))}
          </ol>
        </section>
      )}
    </div>
  );
}

function TranscriptLine({ turn }: { turn: InterviewTurnView }) {
  const isCandidate = turn.role === "candidate";
  return (
    <li className={`iv-turn ${isCandidate ? "iv-turn-me" : "iv-turn-ai"}`}>
      <span className="iv-turn-who">{isCandidate ? "我" : "面试官"}</span>
      <p className="iv-turn-text">{turn.text}</p>
    </li>
  );
}

function AnswerItem({ answer, index }: { answer: InterviewAnswerView; index: number }) {
  return (
    <li className="iv-answer-item">
      <div className="iv-answer-head">
        <span className="iv-answer-index">第 {index + 1} 题</span>
        <span className="iv-tag">{gradeLabel(answer.gradeStatus)}</span>
      </div>
      <p className="iv-answer-q">{answer.question}</p>
      <p className="iv-answer-a">{answer.text}</p>
    </li>
  );
}
