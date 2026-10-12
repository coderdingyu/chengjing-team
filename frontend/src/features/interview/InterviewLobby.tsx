import { useEffect, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { interviewApi } from "./interviewApi";
import type { InterviewCatalogView, InterviewRoomView } from "./types";
import { statusLabel } from "./types";
import "./interview.css";

/**
 * Owner: member C. C01 — the lobby that opens a room from a role and a target duration.
 *
 * The room itself is created on the server; this screen only collects the plan and lists the rooms
 * the caller may continue.
 */
export function InterviewLobby() {
  const navigate = useNavigate();
  const [catalog, setCatalog] = useState<InterviewCatalogView>();
  const [rooms, setRooms] = useState<InterviewRoomView[]>([]);
  const [role, setRole] = useState("");
  const [level, setLevel] = useState("中级");
  const [minutes, setMinutes] = useState(15);
  const [focus, setFocus] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    let cancelled = false;
    Promise.all([
      interviewApi.get<InterviewCatalogView>("/interviews/catalog"),
      interviewApi.get<InterviewRoomView[]>("/interviews"),
    ])
      .then(([options, mine]) => {
        if (cancelled) return;
        setCatalog(options);
        setRooms(mine);
        setRole(options.roles[0] ?? "");
        setLevel(options.levels[1] ?? options.levels[0] ?? "中级");
      })
      .catch((problem: Error) => {
        if (!cancelled) setError(problem.message);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  async function start() {
    setBusy(true);
    setError("");
    try {
      const room = await interviewApi.post<InterviewRoomView>("/interviews", {
        role,
        level,
        minutes,
        focus,
      });
      navigate(`/interviews/${room.id}`);
    } catch (problem) {
      setError((problem as Error).message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <>
      <header className="iv-heading">
        <span className="eyebrow">成员 C · 面试进行</span>
        <h1>创建一场完整的面试房间</h1>
        <p>
          选好岗位和目标时长后进入房间。文字作答始终可用，语音与摄像头另外单独征得同意。
        </p>
      </header>

      {error && (
        <p role="alert" className="iv-error">
          {error}
        </p>
      )}

      <div className="iv-lobby">
        <section className="iv-panel">
          <h2>这场想练什么岗位？</h2>
          <form
            onSubmit={(event) => {
              event.preventDefault();
              void start();
            }}
          >
            <label>
              岗位
              <select value={role} onChange={(event) => setRole(event.target.value)}>
                {(catalog?.roles ?? []).map((item) => (
                  <option key={item} value={item}>
                    {item}
                  </option>
                ))}
              </select>
            </label>
            <div className="iv-form-two">
              <label>
                目标级别
                <select value={level} onChange={(event) => setLevel(event.target.value)}>
                  {(catalog?.levels ?? []).map((item) => (
                    <option key={item} value={item}>
                      {item}
                    </option>
                  ))}
                </select>
              </label>
              <label>
                目标时长
                <select
                  value={minutes}
                  onChange={(event) => setMinutes(Number(event.target.value))}
                >
                  {(catalog?.durations ?? [15]).map((item) => (
                    <option key={item} value={item}>
                      {item} 分钟
                    </option>
                  ))}
                </select>
              </label>
            </div>
            <label>
              这次特别想练什么？（可选）
              <textarea
                rows={2}
                maxLength={500}
                value={focus}
                onChange={(event) => setFocus(event.target.value)}
                placeholder="例如：追问时说清自己的取舍依据"
              />
            </label>
            <button className="iv-primary" type="submit" disabled={busy || !catalog}>
              {busy ? "正在准备面试室…" : "进入面试室"}
            </button>
          </form>
          <p className="iv-fineprint">
            房间属于当前账号；刷新页面可以从下面的列表继续，其他人的房间不会被列出。
          </p>
        </section>

        <section className="iv-panel iv-recent">
          <h2>继续上次面试</h2>
          {rooms.length === 0 && <p className="iv-muted">还没有房间，先创建一场。</p>}
          <ul>
            {rooms.map((room) => (
              <li key={room.id}>
                <Link to={`/interviews/${room.id}`}>
                  <span className="iv-recent-main">
                    <strong>{room.title}</strong>
                    <small>
                      {new Date(room.createdAt).toLocaleString("zh-CN")} · 第 {room.stageIndex + 1}/
                      {room.totalStages} 环节
                    </small>
                  </span>
                  <span className="iv-tag">{statusLabel(room.status)}</span>
                </Link>
              </li>
            ))}
          </ul>
        </section>
      </div>
    </>
  );
}
