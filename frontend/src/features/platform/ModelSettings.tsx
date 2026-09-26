import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { api } from "../../api";
import "./model-settings.css";

type Profile = { id: string; name: string; provider: string; baseUrl: string; model: string; hasKey: boolean };
type Preset = { id: string; name: string; baseUrl: string; sampleModel: string };
type Settings = { profiles: Profile[]; routes: Record<string, string>; presets: Preset[] };
type Form = { name: string; provider: string; baseUrl: string; model: string; apiKey: string };
const empty: Form = { name: "", provider: "stepfun", baseUrl: "https://api.stepfun.com/v1", model: "step-5-preview", apiKey: "" };
const purposes = [
  ["dialogue", "面试对话"], ["grading", "证据评分"], ["voice", "语音转写与朗读"], ["live", "实时语音"],
] as const;

function modelApi<T>(path: string, init?: RequestInit): Promise<T> {
  const token = localStorage.getItem("chengjing.token");
  return api<T>(path, { ...init, headers: { ...(token ? { Authorization: `Bearer ${token}` } : {}), ...init?.headers } });
}

export default function ModelSettings() {
  const [settings, setSettings] = useState<Settings | null>(null);
  const [form, setForm] = useState<Form>(empty);
  const [editing, setEditing] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState("");
  const reload = () => modelApi<Settings>("/models").then(setSettings).catch((error: Error) => setMessage(error.message));
  useEffect(() => { void reload(); }, []);

  const choosePreset = (preset: Preset) => {
    setEditing(null);
    setForm({ name: preset.name, provider: preset.id, baseUrl: preset.baseUrl, model: preset.sampleModel, apiKey: "" });
    setMessage("");
  };

  const save = async (event: React.FormEvent) => {
    event.preventDefault(); setBusy(true); setMessage("");
    try {
      await modelApi<Profile>(editing ? `/models/${editing}` : "/models", {
        method: editing ? "PUT" : "POST", body: JSON.stringify(form),
      });
      setForm(empty); setEditing(null); setMessage("配置已保存。密钥仅保存在服务端，不会从接口回传。");
      await reload();
    } catch (error) { setMessage((error as Error).message); }
    finally { setBusy(false); }
  };

  const changeRoute = async (purpose: string, profileId: string) => {
    setMessage("");
    try {
      await modelApi(`/models/routes/${purpose}`, { method: "PUT", body: JSON.stringify({ profileId }) });
      await reload();
    } catch (error) { setMessage((error as Error).message); }
  };

  const testConnection = async (purpose: string) => {
    setMessage("正在检测模型连接…");
    try {
      const result = await modelApi<{ status: string; model: string }>(`/models/test/${purpose}`, { method: "POST" });
      setMessage(`${result.model} 已响应，${purpose === "grading" ? "评分" : "对话"}通道可用。`);
    } catch (error) { setMessage((error as Error).message); }
  };

  const remove = async (profile: Profile) => {
    if (!window.confirm(`删除「${profile.name}」及其用途绑定？`)) return;
    try { await modelApi(`/models/${profile.id}`, { method: "DELETE" }); await reload(); }
    catch (error) { setMessage((error as Error).message); }
  };

  return <div className="model-settings">
    <Link to="/modules/platform" className="back">← 返回平台模块</Link>
    <span className="eyebrow">PLATFORM / PROVIDER STUDIO</span>
    <h1>让每一段 AI 能力，都有明确来源。</h1>
    <p>选择供应商或自定义兼容站点，把对话、评分和语音分别指向合适的模型。密钥只发送到本机服务端。</p>
    {message && <div className="model-notice" role="status">{message}</div>}
    <div className="model-layout">
      <section className="model-panel">
        <h2>01 / 新建连接</h2>
        <div className="preset-list">{(settings?.presets ?? []).map((preset) =>
          <button key={preset.id} type="button" className={form.provider === preset.id ? "selected" : ""} onClick={() => choosePreset(preset)}>{preset.name}</button>
        )}<button type="button" onClick={() => { setEditing(null); setForm({ name: "阶跃 Live", provider: "stepfun", baseUrl: "https://api.stepfun.com/v1", model: "stepaudio-3-realtime-preview", apiKey: "" }); }}>阶跃 Live</button></div>
        <form onSubmit={save} className="model-form">
          <label>配置名称<input value={form.name} maxLength={80} onChange={(e) => setForm({ ...form, name: e.target.value })} required /></label>
          <label>API 根地址<input value={form.baseUrl} type="url" placeholder="https://example.com/v1" onChange={(e) => setForm({ ...form, baseUrl: e.target.value })} required /></label>
          <label>模型 ID<input value={form.model} placeholder="step-5-preview" onChange={(e) => setForm({ ...form, model: e.target.value })} required /></label>
          <label>API Key<input value={form.apiKey} type="password" autoComplete="off" placeholder={editing ? "留空则保持原密钥" : "仅提交至本机服务端"} onChange={(e) => setForm({ ...form, apiKey: e.target.value })} required={!editing} /></label>
          <button type="submit" disabled={busy}>{busy ? "保存中…" : editing ? "更新连接" : "保存连接"}</button>
        </form>
      </section>
      <section className="model-panel">
        <h2>02 / 已保存的连接</h2>
        {!settings?.profiles.length && <p>尚无配置。登录后保存一套模型连接。</p>}
        {settings?.profiles.map((profile) => <div className="saved-profile" key={profile.id}>
          <strong>{profile.name}</strong><span>{profile.provider} · {profile.model}</span>
          <small>{profile.baseUrl} · {profile.hasKey ? "密钥已配置" : "未配置密钥"}</small>
          <div><button type="button" onClick={() => { setEditing(profile.id); setForm({ ...profile, apiKey: "" }); }}>编辑</button><button type="button" onClick={() => void remove(profile)}>删除</button></div>
        </div>)}
        <h2>03 / 能力路由</h2>
        {purposes.map(([purpose, label]) => <div className="model-route-row" key={purpose}><label className="model-route">{label}<select value={settings?.routes[purpose] ?? ""} onChange={(e) => void changeRoute(purpose, e.target.value)}><option value="">未指定</option>{settings?.profiles.map((profile) => <option key={profile.id} value={profile.id}>{profile.name}</option>)}</select></label>{(purpose === "dialogue" || purpose === "grading") && <button type="button" disabled={!settings?.routes[purpose]} onClick={() => void testConnection(purpose)}>检测</button>}</div>)}
      </section>
    </div>
  </div>;
}
