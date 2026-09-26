import { useEffect, useRef, useState } from "react";
import { Link } from "react-router-dom";
import { api } from "../../api";
import "./live.css";

type Ticket = { ticket: string; sessionId: string; model: string; sampleRate: number; voices: string[] };
type Turn = { speaker: "candidate" | "interviewer"; text: string; at: string };
type Transcript = { role: string; events: Turn[] };
type LiveEvent = { type: string; text?: string; audio?: string; message?: string };

function liveApi<T>(path: string, init?: RequestInit): Promise<T> {
  const token = localStorage.getItem("chengjing.token");
  return api<T>(path, { ...init, headers: { ...(token ? { Authorization: `Bearer ${token}` } : {}), ...init?.headers } });
}

/** Full-duplex native model demo; C may reuse it with a room ID after its room API is merged. */
export default function LiveLab() {
  const [role, setRole] = useState("产品经理");
  const [voice, setVoice] = useState("linjiajiejie");
  const [consent, setConsent] = useState(false);
  const [status, setStatus] = useState("未连接");
  const [error, setError] = useState("");
  const [partial, setPartial] = useState("");
  const [turns, setTurns] = useState<Turn[]>([]);
  const [sessionId, setSessionId] = useState(() => localStorage.getItem("chengjing.liveSessionId") ?? "");
  const wsRef = useRef<WebSocket | null>(null);
  const streamRef = useRef<MediaStream | null>(null);
  const audioRef = useRef<AudioContext | null>(null);
  const sourcesRef = useRef(new Set<AudioBufferSourceNode>());
  const nextAt = useRef(0);
  const readyRef = useRef(false);
  const responseRef = useRef(false);
  const firstTurnDoneRef = useRef(false);
  const ignoredAudioRef = useRef(false);
  const closedRef = useRef(true);
  const mountedRef = useRef(true);
  const audioStartRef = useRef(0);
  const epochRef = useRef(0);

  const release = () => {
    epochRef.current++;
    closedRef.current = true; readyRef.current = false; responseRef.current = false;
    wsRef.current?.close(); wsRef.current = null;
    streamRef.current?.getTracks().forEach((track) => track.stop()); streamRef.current = null;
    sourcesRef.current.forEach((source) => { try { source.stop(); } catch { /* already ended */ } });
    sourcesRef.current.clear(); nextAt.current = 0;
    void audioRef.current?.close(); audioRef.current = null;
  };
  useEffect(() => { mountedRef.current = true; return () => { mountedRef.current = false; release(); }; }, []);

  const playPcm = (encoded: string) => {
    const context = audioRef.current;
    if (!context || ignoredAudioRef.current) return;
    const bytes = Uint8Array.from(atob(encoded), (char) => char.charCodeAt(0));
    if (bytes.length % 2 !== 0) return;
    const buffer = context.createBuffer(1, bytes.length / 2, 24000);
    const channel = buffer.getChannelData(0);
    const view = new DataView(bytes.buffer);
    for (let i = 0; i < channel.length; i++) channel[i] = view.getInt16(i * 2, true) / 32768;
    const source = context.createBufferSource(); source.buffer = buffer; source.connect(context.destination);
    const startAt = Math.max(context.currentTime + 0.025, nextAt.current);
    nextAt.current = startAt + buffer.duration;
    sourcesRef.current.add(source);
    if (!audioStartRef.current) audioStartRef.current = performance.now();
    source.onended = () => { sourcesRef.current.delete(source); if (!sourcesRef.current.size && !responseRef.current && mountedRef.current) setStatus("正在听你回答"); };
    source.start(startAt);
  };

  const handle = (event: LiveEvent) => {
    switch (event.type) {
      case "ready": readyRef.current = true; setStatus("面试官准备提问"); break;
      case "response.started": responseRef.current = true; ignoredAudioRef.current = false; audioStartRef.current = 0; setStatus("面试官正在回答"); break;
      case "audio.delta": if (event.audio) playPcm(event.audio); break;
      case "response.done": responseRef.current = false; firstTurnDoneRef.current = true; if (!sourcesRef.current.size) setStatus("正在听你回答"); break;
      case "interviewer.partial": if (!ignoredAudioRef.current) setPartial((old) => old + (event.text ?? "")); break;
      case "interviewer.final": setPartial(""); if (!ignoredAudioRef.current && event.text) setTurns((old) => [...old, { speaker: "interviewer", text: event.text!, at: new Date().toISOString() }]); break;
      case "candidate.final": if (event.text) setTurns((old) => [...old, { speaker: "candidate", text: event.text!, at: new Date().toISOString() }]); break;
      case "warning": case "error": setError(event.message ?? "Live 连接异常"); break;
      default: break;
    }
  };

  const connect = async () => {
    if (!consent || !closedRef.current) return;
    const attempt = ++epochRef.current;
    closedRef.current = false;
    setError(""); setPartial(""); setStatus("正在连接");
    try {
      const ticket = await liveApi<Ticket>("/models/live/ticket", { method: "POST", body: JSON.stringify({ role, sessionId, consent: true }) });
      if (attempt !== epochRef.current) return;
      setSessionId(ticket.sessionId); localStorage.setItem("chengjing.liveSessionId", ticket.sessionId);
      const previous = await liveApi<Transcript>(`/models/live/transcripts/${ticket.sessionId}`);
      if (attempt !== epochRef.current) return;
      setTurns(previous.events ?? []);
      const stream = await navigator.mediaDevices.getUserMedia({ audio: { echoCancellation: true, noiseSuppression: true, channelCount: 1 } });
      if (attempt !== epochRef.current) { stream.getTracks().forEach((track) => track.stop()); return; }
      streamRef.current = stream;
      const context = new AudioContext({ sampleRate: ticket.sampleRate }); audioRef.current = context;
      await context.resume();
      if (context.sampleRate !== ticket.sampleRate) throw new Error("浏览器无法使用 24 kHz 麦克风采样率，请换用支持的浏览器。");
      await context.audioWorklet.addModule("/live-capture.js");
      if (attempt !== epochRef.current) return;
      const capture = new AudioWorkletNode(context, "chengjing-live-capture");
      const source = context.createMediaStreamSource(stream);
      const silent = context.createGain(); silent.gain.value = 0;
      source.connect(capture); capture.connect(silent); silent.connect(context.destination);
      const socket = new WebSocket(`${location.protocol === "https:" ? "wss" : "ws"}://${location.host}/ws/platform/live`);
      wsRef.current = socket;
      capture.port.onmessage = (audio) => {
        if (!readyRef.current || !firstTurnDoneRef.current || responseRef.current || sourcesRef.current.size || socket.readyState !== WebSocket.OPEN || socket.bufferedAmount > 256_000) return;
        const bytes = new Uint8Array(audio.data as ArrayBuffer);
        socket.send(JSON.stringify({ type: "audio", audio: btoa(String.fromCharCode(...bytes)) }));
      };
      socket.onopen = () => socket.send(JSON.stringify({ type: "start", ticket: ticket.ticket, voice, consent: true }));
      socket.onmessage = (message) => { try { handle(JSON.parse(message.data) as LiveEvent); } catch { setError("Live 消息解析失败，可重连继续。"); } };
      socket.onerror = () => setError("Live 网络连接失败；已保存的文字仍在。");
      socket.onclose = () => { release(); if (mountedRef.current) setStatus("已断开，可继续本场面试"); };
    } catch (cause) {
      if (attempt !== epochRef.current) return;
      release(); setStatus("未连接"); setError(cause instanceof Error ? cause.message : "无法开启 Live 语音");
    }
  };

  const interrupt = () => {
    if (!wsRef.current || wsRef.current.readyState !== WebSocket.OPEN) return;
    const playedMs = audioStartRef.current ? Math.max(0, Math.round(performance.now() - audioStartRef.current)) : 0;
    ignoredAudioRef.current = true; responseRef.current = false; firstTurnDoneRef.current = true;
    sourcesRef.current.forEach((source) => { try { source.stop(); } catch { /* already ended */ } });
    sourcesRef.current.clear(); nextAt.current = 0; setPartial(""); setStatus("已手动打断，请继续说");
    wsRef.current.send(JSON.stringify({ type: "interrupt", playedMs }));
  };

  return <div className="live-lab">
    <Link to="/modules/platform" className="back">← 返回平台模块</Link>
    <span className="eyebrow">PLATFORM / NATIVE LIVE</span>
    <h1>一场能自然对话的模拟面试。</h1>
    <p>面试官会先把问题说完，你再回答。需要提前发言时，可手动打断。断线后用同一场记录继续。</p>
    <div className="live-grid"><section className="live-controls">
      <label>岗位<input value={role} maxLength={80} onChange={(event) => setRole(event.target.value)} disabled={!closedRef.current} /></label>
      <label>面试官声音<select value={voice} onChange={(event) => setVoice(event.target.value)} disabled={!closedRef.current}><option value="linjiajiejie">亲和女声</option><option value="wenrounansheng">温柔男声</option></select></label>
      <label className="live-consent"><input type="checkbox" checked={consent} onChange={(event) => setConsent(event.target.checked)} />我同意将麦克风声音发送到已配置的阶跃 Live 模型；系统只保存转写文字，不保存原始音频。</label>
      <div className="live-actions">{status === "未连接" || status.startsWith("已断开") ? <button type="button" disabled={!consent} onClick={() => void connect()}>开始 / 继续 Live</button> : <><button type="button" onClick={interrupt} disabled={!responseRef.current && !sourcesRef.current.size}>手动打断</button><button type="button" onClick={() => { release(); setStatus("已断开，可继续本场面试"); }}>结束本次连接</button></>}</div>
      {closedRef.current && sessionId && <button type="button" className="live-new" onClick={() => { localStorage.removeItem("chengjing.liveSessionId"); setSessionId(""); setTurns([]); setPartial(""); setError(""); }}>开始新一场</button>}
      <p role="status" className="live-status"><span className="live-dot" />{status}</p>
      {error && <p role="alert" className="live-error">{error}</p>}
      <p className="live-note">Live 使用 StepAudio 3 Realtime。Step 5 Preview 是文本模型，请在模型配置中为 Live 单独绑定实时语音模型。</p>
    </section><section className="live-transcript"><h2>实时文字记录</h2>{turns.length === 0 && <p>连接后，面试问题和你的回答会出现在这里。</p>}{turns.map((turn, index) => <div className={`live-turn ${turn.speaker}`} key={`${turn.at}-${index}`}><small>{turn.speaker === "candidate" ? "你" : "面试官"}</small><p>{turn.text}</p></div>)}{partial && <div className="live-turn interviewer"><small>面试官 · 正在说</small><p>{partial}</p></div>}</section></div>
  </div>;
}
