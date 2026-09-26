import { useState } from "react";
import { Link } from "react-router-dom";
import VoicePanel from "./VoicePanel";
import "./voice.css";

export default function VoiceLab() {
  const [answer, setAnswer] = useState("");
  return <div className="voice-lab">
    <Link to="/modules/platform" className="back">← 返回平台模块</Link>
    <span className="eyebrow">PLATFORM / VOICE STUDIO</span>
    <h1>先听清，再完整作答。</h1>
    <VoicePanel question="请用一个真实项目举例，说明你遇到的最大取舍，以及你如何验证最后的结果。" onConfirm={(text) => setAnswer((old) => old ? `${old}\n${text}` : text)} />
    <label className="voice-answer">回答草稿<textarea value={answer} onChange={(event) => setAnswer(event.target.value)} rows={7} placeholder="确认后的文字会加入这里；在面试模块接入后再正式提交。" /></label>
  </div>;
}
