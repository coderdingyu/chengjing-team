import { useCallback, useEffect, useRef, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { interviewApi } from "./interviewApi";
import type {
  InterviewAnswerView,
  InterviewRoomView,
  InterviewTurnResultView,
  InterviewTurnView,
} from "./types";
import { INTENT_ACTIONS, gradeLabel, intentLabel, newRequestId, statusLabel } from "./types";
import "./interview.css";

/**
 * Owner: member C.
 *
 * C01 — reopen a room that already exists.
 * C02 — text answers with an ordered transcript.
 * C03 — clarification / replay / thinking requests that are answered but never scored.
 *
 * The room is loaded from the server by id, so a browser refresh lands on the same room instead of
 * creating a new one, and a room owned by another account answers with "不存在".
 *
 * A submission carries a `requestId` that is minted once per distinct draft (or per intent) and
 * reused on a retry, so clicking "提交" twice — or a network resend — records it exactly once.
 */

/** Binds a submit id to the thing it was minted for, so a retry replays instead of duplicating. */
function useSubmissionIds() {
  const ref = useRef<{ text: string; id: string }>({ text: "", id: "" });
  const intents = useRef<Record<string, string>>({});

  const idForText = useCallback((text: string) => {
    if (ref.current.id === "" || ref.current.text !== text) {
      ref.current = { text, id: newRequestId() };
    }
    return ref.current.id;
  }, []);

  const idForIntent = useCallback((intent: string) => {
    intents.current[intent] ??= newRequestId();
    return intents.current[intent];
  }, []);

  const forgetIntent = useCallback((intent: string) => {
    delete intents.current[intent];
  }, []);

  const forgetAllIntents = useCallback(() => {
    intents.current = {};
  }, []);

  return { idForText, idForIntent, forgetIntent, forgetAllIntents };
}

export function InterviewRoom() {
  const { id = "" } = useParams();
  const [room, setRoom] = useState<InterviewRoomView>();
  const [loadError, setLoadError] = useState("");
  const [draft, setDraft] = useState("");
  const [sendError, setSendError] = useState("");
  const [sending, setSending] = useState(false);
  const [dialogueSource, setDialogueSource] = useState("");
  const { idForText, idForIntent, forgetIntent, forgetAllIntents } = useSubmissionIds();

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

  // A new stage means new questions: the previous request ids belong to the old question.
  const stageIndex = room?.stageIndex;
  useEffect(() => {
    forgetAllIntents();
  }, [forgetAllIntents, stageIndex]);

  const applyResult = useCallback((result: InterviewTurnResultView) => {
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
  }, []);

  const submit = useCallback(async () => {
    if (!room || sending) return;
    const text = draft.trim();
    if (!text) return;

    const requestId = idForText(text);
    setSending(true);
    setSendError("");
    try {
      const result = await interviewApi.post<InterviewTurnResultView>(`/interviews/${room.id}/turns`, {
        requestId,
        questionId: room.questionId,
        text,
      });
      applyResult(result);
      setDraft("");
    } catch (problem) {
      setSendError(problem instanceof Error ? problem.message : "提交失败，请重试");
    } finally {
      setSending(false);
    }
  }, [applyResult, draft, idForText, room, sending]);

  const requestIntent = useCallback(
    async (intent: string) => {
      if (!room || sending) return;
      const requestId = idForIntent(intent);
      setSending(true);
      setSendError("");
      try {
        const result = await interviewApi.post<InterviewTurnResultView>(
          `/interviews/${room.id}/requests`,
          { requestId, questionId: room.questionId, intent },
        );
        applyResult(result);
        forgetIntent(intent);
      } catch (problem) {
        setSendError(problem instanceof Error ? problem.message : "请求失败，请重试");
      } finally {
        setSending(false);
      }
    },
    [applyResult, forgetIntent, idForIntent, room, sending],
  );

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
          <span>已作答 {room.answers.length} 题</span>
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

          <div className="iv-intent-row">
            <span className="iv-intent-hint">面试中可随时请求（不计入作答）</span>
            <div className="iv-intent-buttons">
              {INTENT_ACTIONS.map((action) => (
                <button
                  key={action.intent}
                  type="button"
                  className="iv-intent-btn"
                  title={action.hint}
                  onClick={() => void requestIntent(action.intent)}
                  disabled={sending}
                >
                  {action.label}
                </button>
              ))}
            </div>
          </div>
        </section>

        <section className="iv-panel iv-conversation">
          <h2>对话记录</h2>
          {room.turns.length === 0 ? (
            <p className="iv-muted">
              还没有记录。写下回答并提交，或先用下方的请求让面试官澄清、重听、给你思考时间。
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
                ? "澄清、重听、思考与追问目前由本地确定性规则生成（尚未接入语音/模型网关），提交内容仅保存在本次面试房间中。"
                : "回应来自外部对话服务。"}
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
  const badge = intentLabel(turn.intent);
  return (
    <li className={`iv-turn ${isCandidate ? "iv-turn-me" : "iv-turn-ai"}`}>
      <span className="iv-turn-who">
        {isCandidate ? "我" : "面试官"}
        {badge && <em className="iv-turn-intent">{badge}</em>}
      </span>
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
