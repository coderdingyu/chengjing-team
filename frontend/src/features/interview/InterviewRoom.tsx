import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { interviewApi } from "./interviewApi";
import type { InterviewRoomView } from "./types";
import { statusLabel } from "./types";
import "./interview.css";

/**
 * Owner: member C. C01 — reopen a room that already exists.
 *
 * The room is loaded from the server by id, so a browser refresh lands on the same room instead of
 * creating a new one, and a room owned by another account answers with "不存在".
 */
export function InterviewRoom() {
  const { id = "" } = useParams();
  const [room, setRoom] = useState<InterviewRoomView>();
  const [error, setError] = useState("");

  useEffect(() => {
    let cancelled = false;
    interviewApi
      .get<InterviewRoomView>(`/interviews/${id}`)
      .then((loaded) => {
        if (!cancelled) setRoom(loaded);
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
        <p className="iv-muted">正在打开面试室…</p>
      </div>
    );
  }

  const progress = Math.round(((room.stageIndex + 1) / room.totalStages) * 100);

  return (
    <div className="iv-room">
      <Link to="/interviews" className="iv-back">
        ← 返回面试大厅
      </Link>

      <header className="iv-room-heading">
        <div>
          <span className="eyebrow">{room.role} · {room.level}</span>
          <h1>{room.title}</h1>
        </div>
        <div className="iv-room-meta">
          <span className={`iv-badge iv-badge-${room.status.toLowerCase()}`}>
            {statusLabel(room.status)}
          </span>
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
          <p className="iv-muted">
            房间已建立。文字作答、澄清与暂停控制将在接下来的提交中接到这里。
          </p>
        </section>
      </div>
    </div>
  );
}
