import { FormEvent, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { api } from "../../api";
import { useSession, type Account } from "./session";
import "./identity.css";

export default function LoginPage() {
  const navigate = useNavigate();
  const { signIn } = useSession();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  const canSubmit = !busy && email.trim() !== "" && password !== "";

  async function submit(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError("");
    try {
      const result = await api<{ token: string; user: Account }>("/auth/login", {
        method: "POST",
        body: JSON.stringify({ email, password }),
      });
      signIn(result.token, result.user);
      navigate("/");
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="id-card">
      <span className="id-eyebrow">CHENGJING · 账号</span>
      <h1>欢迎回来</h1>
      <p className="id-lede">用注册时的邮箱和密码继续你的准备与练习。</p>
      <form className="id-form" onSubmit={submit} noValidate>
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
            autoComplete="current-password"
            required
          />
        </label>
        {error && (
          <p className="id-error" role="alert">
            {error}
          </p>
        )}
        <button className="id-button primary" disabled={!canSubmit} type="submit">
          {busy ? "正在登录…" : "登录"}
        </button>
      </form>
      <p className="id-fineprint">
        还没有账号？<Link to="/register">去注册</Link>
      </p>
    </div>
  );
}
