import { FormEvent, useEffect, useState } from "react";
import { Link, Navigate } from "react-router-dom";
import { api } from "../../api";
import { useSession, type Account } from "./session";
import "./identity.css";

/** Formats the server's ISO timestamp for display; falls back to the raw value if unparsable. */
function formatCreatedAt(value: string): string {
  const parsed = new Date(value);
  return Number.isNaN(parsed.getTime())
    ? value
    : parsed.toLocaleDateString("zh-CN", {
        year: "numeric",
        month: "long",
        day: "numeric",
      });
}

export default function AccountPage() {
  const { account, ready, updateAccount } = useSession();
  const [displayName, setDisplayName] = useState("");
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const [busy, setBusy] = useState(false);

  // Keep the field in step with the account the session holds.
  useEffect(() => {
    if (account) setDisplayName(account.displayName);
  }, [account]);

  if (!ready) return null;
  if (!account) return <Navigate to="/login" replace />;

  const trimmed = displayName.trim();
  const canSubmit = !busy && trimmed !== "" && trimmed !== account.displayName;

  async function save(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError("");
    setMessage("");
    try {
      // The server decides what is stored; the session is updated from its answer, not from
      // the value typed here, so a rejected or trimmed value cannot linger in the UI.
      updateAccount(
        await api<Account>("/account", {
          method: "PUT",
          body: JSON.stringify({ displayName }),
        }),
      );
      setMessage("资料已保存。");
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="id-card id-account">
      <span className="id-eyebrow">CHENGJING · 账号</span>
      <h1>个人资料</h1>
      <p className="id-lede">这是你自己的账号信息。其他用户看不到，也改不了。</p>

      <dl className="id-facts">
        <div>
          <dt>邮箱</dt>
          <dd>{account.email}</dd>
        </div>
        <div>
          <dt>注册时间</dt>
          <dd>{formatCreatedAt(account.createdAt)}</dd>
        </div>
      </dl>

      <form className="id-form" onSubmit={save} noValidate>
        <label className="id-field">
          <span>你的称呼</span>
          <input
            value={displayName}
            onChange={(e) => setDisplayName(e.target.value)}
            maxLength={80}
            autoComplete="name"
            aria-describedby="id-name-rule"
            required
          />
        </label>
        <p id="id-name-rule" className="id-hint">
          最多 80 个字。这个称呼会显示在你的练习记录里。
        </p>
        {message && (
          <p className="id-notice" role="status">
            {message}
          </p>
        )}
        {error && (
          <p className="id-error" role="alert">
            {error}
          </p>
        )}
        <button className="id-button primary" disabled={!canSubmit} type="submit">
          {busy ? "正在保存…" : "保存资料"}
        </button>
      </form>

      <p className="id-fineprint">
        <Link to="/">← 返回工作台</Link>
      </p>
    </div>
  );
}
