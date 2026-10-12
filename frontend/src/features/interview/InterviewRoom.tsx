import { useCallback, useEffect, useRef, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { interviewApi } from "./interviewApi";
import { stopAllCapture } from "./localCapture";
import type {
  InterviewAnswerView,
  InterviewRoomView,
  InterviewTurnResultView,
  InterviewTurnView,
} from "./types";
import {
  INTENT_ACTIONS,
  formatClock,
  gradeLabel,
  intentLabel,
  newRequestId,
  statusLabel,
} from "./types";
import "./interview.css";

/**
 * Owner: member C.
 *
 * C01 — reopen a room that already exists.
 * C02 — text answers with an ordered transcript.
 * C03 — clarification / replay / thinking requests answered but never scored.
 * C04 — restorable draft, clock control (pause / resume) and ending into the review page.
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

const DRAFT_SAVE_DELAY_MS = 600;

export function InterviewRoom() {
  const { id = "" } = useParams();
  const navigate = useNavigate();
  const [room, setRoom] = useState<InterviewRoomView>();
  const [loadError, setLoadError] = useState("");
  const [draft, setDraft] = useState("");
  const [draftState, setDraftState] = useState<"idle" | "saving" | "saved">("idle");
  const [elapsed, setElapsed] = useState(0);
  const [stageClock, setStageClock] = useState(0);
  const [sendError, setSendError] = useState("");
  const [busy, setBusy] = useState(false);
  const [dialogueSource, setDialogueSource] = useState("");
  const { idForText, idForIntent, forgetIntent, forgetAllIntents } = useSubmissionIds();

  useEffect(() => {
    let cancelled = false;
    interviewApi
      .get<InterviewRoomView>(`/interviews/${id}`)
      .then((loaded) => {
        if (cancelled) return;
        setRoom(loaded);
        setDraft(loaded.draft);
        setLoadError("");
      })
      .catch((problem: Error) => {
        if (!cancelled) setLoadError(problem.message);
      });
    return () => {
      cancelled = true;
    };
  }, [id]);

  const stageIndex = room?.stageIndex;
  const status = room?.status;

  // A new stage means new questions: the previous request ids belong to the old question.
  useEffect(() => {
    forgetAllIntents();
  }, [forgetAllIntents, stageIndex]);

  // Local ticking so the clock reads smoothly; the server value re-syncs on every room update.
  const serverElapsed = room?.elapsedSeconds ?? 0;
  useEffect(() => {
    setElapsed(serverElapsed);
  }, [serverElapsed, id]);

  useEffect(() => {
    if (status !== "ACTIVE") return;
    const timer = setInterval(() => setElapsed((value) => value + 1), 1000);
    return () => clearInterval(timer);
  }, [status, id]);

  // The current stage has its own clock, used for the "预计时长" hint.
  const serverStageElapsed = room?.stageElapsedSeconds ?? 0;
  useEffect(() => {
    setStageClock(serverStageElapsed);
  }, [serverStageElapsed, id, stageIndex]);

  useEffect(() => {
    if (status !== "ACTIVE") return;
    const timer = setInterval(() => setStageClock((value) => value + 1), 1000);
    return () => clearInterval(timer);
  }, [status, id, stageIndex]);

  // Autosave the draft to the server so a refresh — even on another device — restores it.
  const savedDraft = room?.draft ?? "";
  useEffect(() => {
    if (!room || room.status === "COMPLETED" || draft === savedDraft) return;
    const handle = setTimeout(() => {
      setDraftState("saving");
      interviewApi
        .put<InterviewRoomView>(`/interviews/${room.id}/draft`, { text: draft })
        .then((updated) => {
          setRoom((current) => (current ? { ...current, draft: updated.draft } : current));
          setDraftState("saved");
        })
        .catch(() => setDraftState("idle"));
    }, DRAFT_SAVE_DELAY_MS);
    return () => clearTimeout(handle);
  }, [draft, savedDraft, room]);

  const applyTurnResult = useCallback((result: InterviewTurnResultView) => {
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
    if (!room || busy || room.status !== "ACTIVE") return;
    const text = draft.trim();
    if (!text) return;

    const requestId = idForText(text);
    setBusy(true);
    setSendError("");
    try {
      const result = await interviewApi.post<InterviewTurnResultView>(`/interviews/${room.id}/turns`, {
        requestId,
        questionId: room.questionId,
        text,
      });
      applyTurnResult(result);
      setDraft("");
      setRoom((current) => (current ? { ...current, draft: "" } : current));
      setDraftState("idle");
    } catch (problem) {
      setSendError(problem instanceof Error ? problem.message : "提交失败，请重试");
    } finally {
      setBusy(false);
    }
  }, [applyTurnResult, busy, draft, idForText, room]);

  const requestIntent = useCallback(
    async (intent: string) => {
      if (!room || busy || room.status !== "ACTIVE") return;
      const requestId = idForIntent(intent);
      setBusy(true);
      setSendError("");
      try {
        const result = await interviewApi.post<InterviewTurnResultView>(
          `/interviews/${room.id}/requests`,
          { requestId, questionId: room.questionId, intent },
        );
        applyTurnResult(result);
        forgetIntent(intent);
      } catch (problem) {
        setSendError(problem instanceof Error ? problem.message : "请求失败，请重试");
      } finally {
        setBusy(false);
      }
    },
    [applyTurnResult, busy, forgetIntent, idForIntent, room],
  );

  const control = useCallback(
    async (action: "pause" | "resume" | "finish") => {
      if (!room || busy) return;
      setBusy(true);
      setSendError("");
      try {
        const updated = await interviewApi.post<InterviewRoomView>(`/interviews/${room.id}/${action}`, {});
        setRoom(updated);
        // Pausing and ending must not leave the microphone running.
        if (action !== "resume") stopAllCapture();
        if (action === "finish") navigate(`/interviews/${room.id}/review`);
      } catch (problem) {
        setSendError(problem instanceof Error ? problem.message : "操作失败，请重试");
      } finally {
        setBusy(false);
      }
    },
    [busy, navigate, room],
  );

  // C05 — going to the next stage must not lose what the candidate was typing: the local draft is
  // kept on top of the server response even when the debounced save has not fired yet.
  const goNextStage = useCallback(async () => {
    if (!room || busy || room.status !== "ACTIVE" || room.isLastStage) return;
    setBusy(true);
    setSendError("");
    try {
      const updated = await interviewApi.post<InterviewRoomView>(`/interviews/${room.id}/stage/next`, {});
      setRoom({ ...updated, draft });
      forgetAllIntents();
    } catch (problem) {
      setSendError(problem instanceof Error ? problem.message : "推进失败，请重试");
    } finally {
      setBusy(false);
    }
  }, [busy, draft, forgetAllIntents, room]);

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

  const active = room.status === "ACTIVE";
  const completed = room.status === "COMPLETED";
  const progress = Math.round(((room.stageIndex + 1) / room.totalStages) * 100);
  const canSubmit = draft.trim().length > 0 && !busy && active;
  // Over time is a hint, never a stop: the interview keeps running.
  const overStage = room.stageExpectedSeconds > 0 && stageClock > room.stageExpectedSeconds;

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
          <span className="iv-clock" aria-label="已用时">
            ⏱ {formatClock(elapsed)}
          </span>
          <span>已作答 {room.answers.length} 题</span>
          <span>目标时长 {room.minutes} 分钟</span>
        </div>
      </header>

      <div className="iv-controls" role="group" aria-label="面试控制">
        {active && (
          <button type="button" className="iv-control-btn" onClick={() => void control("pause")} disabled={busy}>
            暂停面试
          </button>
        )}
        {room.status === "PAUSED" && (
          <button type="button" className="iv-control-btn iv-control-strong" onClick={() => void control("resume")} disabled={busy}>
            继续面试
          </button>
        )}
        {active && !room.isLastStage && (
          <button type="button" className="iv-control-btn" onClick={() => void goNextStage()} disabled={busy}>
            进入下一环节
          </button>
        )}
        {!completed && (
          <button type="button" className="iv-control-btn iv-control-danger" onClick={() => void control("finish")} disabled={busy}>
            结束并查看复盘
          </button>
        )}
        {completed && (
          <Link className="iv-control-btn iv-control-strong" to={`/interviews/${room.id}/review`}>
            查看复盘
          </Link>
        )}
        {room.status === "PAUSED" && (
          <span className="iv-muted">已暂停：计时与收音均已停止，继续后才可作答。</span>
        )}
      </div>

      <section className="iv-progress" aria-label="环节进度">
        <div className="iv-progress-track">
          <div className="iv-progress-fill" style={{ width: `${progress}%` }} />
        </div>
        <p>
          第 {room.stageIndex + 1} / {room.totalStages} 环节 · {room.stage.title}
        </p>
        <ol className="iv-stage-lineup" aria-label="环节顺序">
          {room.stages.map((title, index) => (
            <li
              key={title}
              aria-current={index === room.stageIndex ? "step" : undefined}
              className={
                index < room.stageIndex
                  ? "iv-stage-done"
                  : index === room.stageIndex
                    ? "iv-stage-current"
                    : "iv-stage-todo"
              }
            >
              <span className="iv-stage-no">{index + 1}</span>
              {title}
            </li>
          ))}
        </ol>
      </section>

      <div className="iv-room-layout">
        <section className="iv-stage-card">
          <span className="iv-stage-label">当前环节 · {room.stage.title}</span>
          <div className="iv-stage-time">
            <span>
              本环节 {formatClock(stageClock)} / 预计 {formatClock(room.stageExpectedSeconds)}
            </span>
            {overStage && <span className="iv-tag iv-tag-warn">超出预计时长，可继续作答</span>}
          </div>
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
                  disabled={busy || !active}
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
            <div className="iv-composer-head">
              <label htmlFor="iv-draft">你的回答</label>
              <span className="iv-draft-state">
                {draftState === "saving" ? "草稿保存中…" : draftState === "saved" ? "草稿已自动保存" : ""}
              </span>
            </div>
            <textarea
              id="iv-draft"
              value={draft}
              onChange={(event) => {
                setDraft(event.target.value);
                setDraftState("idle");
              }}
              rows={5}
              maxLength={8000}
              disabled={completed}
              placeholder={
                active
                  ? "用文字写下你的回答，草稿会自动保存，刷新也不会丢。"
                  : "面试已暂停，继续后可以作答；草稿仍会保留。"
              }
            />
            <div className="iv-composer-foot">
              <span className="iv-muted">{draft.trim().length} / 8000</span>
              <button type="submit" className="iv-primary" disabled={!canSubmit}>
                {busy ? "提交中…" : "提交回答"}
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
