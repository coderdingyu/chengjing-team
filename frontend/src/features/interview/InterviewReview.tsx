import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { interviewApi } from "./interviewApi";
import type { InterviewAnswerView, InterviewRoomView, InterviewTurnView } from "./types";
import { formatClock, gradeLabel, intentLabel, statusLabel } from "./types";
import "./interview.css";

/**
 * Owner: member C. C04 — where "结束面试" lands.
 *
 * It is a read-only summary of the session: the time that was actually counted, the conversation and
 * the answers. Scoring belongs to member D, so an answer is shown as "待评分" until D's module fills
 * the grade in — this page never invents a score.
 */
export function InterviewReview() {
  const { id = "" } = useParams();
  const [room, setRoom] = useState<InterviewRoomView>();
  const [error, setError] = useState("");

  useEffect(() => {
    let cancelled = false;
    interviewApi
      .get<InterviewRoomView>(`/interviews/${id}`)
      .then((loaded) => {
        if (!cancelled) {
          setRoom(loaded);
          setError("");
        }
      })
      .catch((problem: Error) => {
        if (!cancelled) setError(problem.message);
      });
    return () => {
      cancelled = true;
    };
  }, [id]);

  if (error) {
    return (
      <div className="iv-room">
        <Link to="/interviews" className="iv-back">
          ← 返回面试大厅
        </Link>
        <p role="alert" className="iv-error">
          {error}
        </p>
      </div>
    );
  }

  if (!room) {
    return (
      <div className="iv-room">
        <p className="iv-muted">正在生成复盘…</p>
      </div>
    );
  }

  const answered = room.answers.length;
  const targetSeconds = room.minutes * 60;
  const overTime = room.elapsedSeconds > targetSeconds;

  return (
    <div className="iv-room">
      <Link to="/interviews" className="iv-back">
        ← 返回面试大厅
      </Link>

      <header className="iv-room-heading">
        <div>
          <span className="eyebrow">面试复盘</span>
          <h1>{room.title}</h1>
        </div>
        <div className="iv-room-meta">
          <span className={`iv-badge iv-badge-${room.status.toLowerCase()}`}>
            {statusLabel(room.status)}
          </span>
          <span>实际用时 {formatClock(room.elapsedSeconds)}</span>
          <span>目标时长 {room.minutes} 分钟</span>
        </div>
      </header>

      {room.status !== "COMPLETED" && (
        <p className="iv-error">
          本场面试还在进行中，复盘内容会在结束后定格。
          <Link to={`/interviews/${room.id}`} className="iv-inline-link">
            回到面试室
          </Link>
        </p>
      )}

      <div className="iv-review-grid">
        <section className="iv-panel">
          <h2>本场概览</h2>
          <dl className="iv-summary">
            <div>
              <dt>岗位</dt>
              <dd>{room.role}</dd>
            </div>
            <div>
              <dt>级别</dt>
              <dd>{room.level}</dd>
            </div>
            <div>
              <dt>环节</dt>
              <dd>
                已完成 {Math.min(room.stageIndex + 1, room.totalStages)} / {room.totalStages}
              </dd>
            </div>
            <div>
              <dt>正式作答</dt>
              <dd>{answered} 题</dd>
            </div>
            <div>
              <dt>对话轮次</dt>
              <dd>{room.turns.length} 条</dd>
            </div>
            <div>
              <dt>用时</dt>
              <dd>
                {formatClock(room.elapsedSeconds)}
                {overTime && <span className="iv-tag iv-tag-warn">超出目标</span>}
              </dd>
            </div>
          </dl>
          <p className="iv-fineprint">
            评分由评分模块（成员 D）在回答文本上完成；复盘页只展示对话与作答，不自行打分。
          </p>
        </section>

        <section className="iv-panel">
          <h2>对话回顾</h2>
          {room.turns.length === 0 ? (
            <p className="iv-muted">本场没有留下对话记录。</p>
          ) : (
            <ol className="iv-transcript iv-transcript-static" aria-label="对话回顾">
              {room.turns.map((turn) => (
                <ReviewTurn key={turn.id} turn={turn} />
              ))}
            </ol>
          )}
        </section>
      </div>

      <section className="iv-panel iv-answers">
        <h2>我的作答</h2>
        {answered === 0 ? (
          <p className="iv-muted">本场没有正式作答记录。澄清、重听与思考请求不计入作答。</p>
        ) : (
          <ol className="iv-answer-list">
            {room.answers.map((answer, index) => (
              <AnswerItem key={answer.id} answer={answer} index={index} />
            ))}
          </ol>
        )}
      </section>
    </div>
  );
}

function ReviewTurn({ turn }: { turn: InterviewTurnView }) {
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
