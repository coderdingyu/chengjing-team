import { useEffect, useRef, useState } from "react";

type SpeechResult = { isFinal: boolean; 0: { transcript: string } };
type SpeechResultEvent = { resultIndex: number; results: ArrayLike<SpeechResult> };
type BrowserRecognizer = {
  lang: string; continuous: boolean; interimResults: boolean;
  onresult: ((event: SpeechResultEvent) => void) | null;
  onerror: ((event: { error: string }) => void) | null;
  onend: (() => void) | null;
  start(): void; stop(): void; abort(): void;
};
type SpeechWindow = Window & {
  SpeechRecognition?: new () => BrowserRecognizer;
  webkitSpeechRecognition?: new () => BrowserRecognizer;
};

/** Browser speech fallback. Only confirmed text leaves this component; no raw audio is stored. */
export default function VoicePanel({ question, onConfirm }: { question: string; onConfirm: (text: string) => void }) {
  const [consent, setConsent] = useState(false);
  const [listening, setListening] = useState(false);
  const [speaking, setSpeaking] = useState(false);
  const [partial, setPartial] = useState("");
  const [draft, setDraft] = useState("");
  const [error, setError] = useState("");
  const recognizer = useRef<BrowserRecognizer | null>(null);
  const mounted = useRef(true);

  useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
      recognizer.current?.abort();
      recognizer.current = null;
      window.speechSynthesis?.cancel();
    };
  }, []);

  const readQuestion = () => {
    if (!window.speechSynthesis) { setError("当前浏览器不支持语音朗读，请直接阅读题目。"); return; }
    if (listening) { setError("请先结束转写，再朗读问题。"); return; }
    window.speechSynthesis.cancel();
    const utterance = new SpeechSynthesisUtterance(question);
    utterance.lang = "zh-CN";
    utterance.rate = 0.95;
    utterance.onend = () => mounted.current && setSpeaking(false);
    utterance.onerror = () => { if (mounted.current) { setSpeaking(false); setError("朗读失败，请直接阅读题目。"); } };
    setSpeaking(true); setError("");
    window.speechSynthesis.speak(utterance);
  };

  const start = () => {
    if (!consent || speaking) return;
    const speechWindow = window as SpeechWindow;
    const Constructor = speechWindow.SpeechRecognition ?? speechWindow.webkitSpeechRecognition;
    if (!Constructor) { setError("当前浏览器不支持语音转写，请使用文字作答或换用支持语音识别的浏览器。"); return; }
    const instance = new Constructor();
    instance.lang = "zh-CN";
    instance.continuous = true;
    instance.interimResults = true;
    instance.onresult = (event) => {
      let interim = "";
      let final = "";
      for (let i = event.resultIndex; i < event.results.length; i++) {
        const result = event.results[i];
        if (result.isFinal) final += result[0].transcript;
        else interim += result[0].transcript;
      }
      if (final) setDraft((previous) => `${previous}${previous && !previous.endsWith("\n") ? "\n" : ""}${final}`);
      setPartial(interim);
    };
    instance.onerror = (event) => {
      setError(event.error === "not-allowed" ? "麦克风权限未授予，仍可文字作答。" : "语音转写中断，已确认的文字仍在下方。");
      setListening(false); setPartial(""); recognizer.current = null;
    };
    instance.onend = () => { if (mounted.current) { setListening(false); setPartial(""); recognizer.current = null; } };
    try { instance.start(); recognizer.current = instance; setListening(true); setError(""); }
    catch { setError("无法开启麦克风，请检查浏览器权限。"); }
  };

  const stop = () => { recognizer.current?.stop(); setListening(false); setPartial(""); };
  const confirm = () => { if (draft.trim()) { onConfirm(draft.trim()); setDraft(""); } };

  return <section className="voice-panel" aria-label="传统语音练习">
    <div className="voice-heading"><span className={listening ? "voice-orb active" : "voice-orb"}>声</span><div><strong>语音练习</strong><small role="status">{listening ? "正在听你回答" : speaking ? "正在朗读题目" : "可先听完整题目，再开始作答"}</small></div></div>
    <p className="voice-question">{question}</p>
    <label className="voice-consent"><input type="checkbox" checked={consent} onChange={(e) => setConsent(e.target.checked)} />我同意浏览器的语音服务处理本次麦克风输入；澄镜只接收识别文字，不保存原始音频。</label>
    <div className="voice-actions">
      <button type="button" onClick={readQuestion} disabled={listening}>朗读题目</button>
      {listening ? <button type="button" onClick={stop}>结束转写</button> : <button type="button" onClick={start} disabled={!consent || speaking}>开始回答</button>}
      {speaking && <button type="button" onClick={() => { window.speechSynthesis.cancel(); setSpeaking(false); }}>停止朗读</button>}
    </div>
    {partial && <p className="voice-partial" aria-live="polite">识别中：{partial}</p>}
    <label className="voice-draft">确认后的字幕（可编辑）<textarea value={draft} onChange={(e) => setDraft(e.target.value)} placeholder="说完后字幕会出现在这里；也可以直接键入或修改。" rows={6} /></label>
    <button type="button" className="voice-confirm" disabled={!draft.trim()} onClick={confirm}>将文字加入回答</button>
    {error && <p className="voice-error" role="alert">{error}</p>}
  </section>;
}
