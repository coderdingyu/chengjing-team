import { FormEvent, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { api } from "../../api";
import "./identity.css";

/** Mirrors the server's response for a created account (com.chengjing.identity.AccountView). */
type Account = {
  id: string;
  email: string;
  displayName: string;
  createdAt: string;
};

/** Client-side mirror of PasswordRules, so the field can explain the rule before the round trip. */
const PASSWORD_MIN = 8;
const PASSWORD_MAX = 64;

export default function RegisterPage() {
  const navigate = useNavigate();
  const [displayName, setDisplayName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [created, setCreated] = useState<Account | null>(null);
  const [busy, setBusy] = useState(false);

  const tooShort = password.length > 0 && password.length < PASSWORD_MIN;
  const canSubmit =
    !busy && displayName.trim() !== "" && email.trim() !== "" && !tooShort;

  async function submit(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError("");
    try {
      // The server normalises the address and is the authority on every rule here.
      setCreated(
        await api<Account>("/auth/register", {
          method: "POST",
          body: JSON.stringify({ email, password, displayName }),
        }),
      );
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }

  if (created) {
    return (
      <div className="id-card">
        <span className="id-eyebrow">CHENGJING · 账号</span>
        <h1>账号已创建</h1>
        <p className="id-lede">
          欢迎，{created.displayName}。接下来用 <strong>{created.email}</strong>{" "}
          登录，开始整理你的面试经历。
        </p>
        <button
          className="id-button primary"
          onClick={() => navigate("/login")}
          type="button"
        >
          去登录
        </button>
      </div>
    );
  }

  return (
    <div className="id-card">
      <span className="id-eyebrow">CHENGJING · 账号</span>
      <h1>创建你的账号</h1>
      <p className="id-lede">
        注册后即可保存准备计划、面试记录与复盘。我们只保存密码的哈希，不保存明文。
      </p>
      <form className="id-form" onSubmit={submit} noValidate>
        <label className="id-field">
          <span>你的称呼</span>
          <input
            value={displayName}
            onChange={(e) => setDisplayName(e.target.value)}
            maxLength={80}
            autoComplete="name"
            required
          />
        </label>
        <label className="id-field">
          <span>邮箱</span>
          <input
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            maxLength={190}
            autoComplete="email"
            required
          />
        </label>
        <label className="id-field">
          <span>密码</span>
          <input
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            maxLength={PASSWORD_MAX}
            autoComplete="new-password"
            aria-describedby="id-password-rule"
            required
          />
        </label>
        <p
          id="id-password-rule"
          className={tooShort ? "id-hint invalid" : "id-hint"}
        >
          {PASSWORD_MIN}—{PASSWORD_MAX} 位。多字节字符请适当缩短。
        </p>
        {error && (
          <p className="id-error" role="alert">
            {error}
          </p>
        )}
        <button className="id-button primary" disabled={!canSubmit} type="submit">
          {busy ? "正在创建…" : "创建账号"}
        </button>
      </form>
      <p className="id-fineprint">
        已有账号？<Link to="/login">去登录</Link>
      </p>
    </div>
  );
}
