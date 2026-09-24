import { FormEvent, useEffect, useState } from "react";
import { Link, Navigate, useNavigate } from "react-router-dom";
import { api } from "../../api";
import { useSession, type Account } from "./session";
import "./identity.css";

/** Client-side mirror of PasswordRules. */
const PASSWORD_MIN = 8;
const PASSWORD_MAX = 64;

/**
 * Must match AccountService.CONFIRMATION_PHRASE on the server, which is the authority: the phrase
 * is checked there too, so a caller cannot erase an account without restating it.
 */
const ERASE_PHRASE = "注销我的账号";

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
  const navigate = useNavigate();
  const { account, ready, signOut, updateAccount } = useSession();

  const [displayName, setDisplayName] = useState("");
  const [profileError, setProfileError] = useState("");
  const [profileMessage, setProfileMessage] = useState("");
  const [profileBusy, setProfileBusy] = useState(false);

  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [passwordError, setPasswordError] = useState("");
  const [passwordBusy, setPasswordBusy] = useState(false);

  const [exportError, setExportError] = useState("");
  const [exportMessage, setExportMessage] = useState("");
  const [exportBusy, setExportBusy] = useState(false);

  const [eraseOpen, setEraseOpen] = useState(false);
  const [erasePassword, setErasePassword] = useState("");
  const [eraseConfirm, setEraseConfirm] = useState("");
  const [eraseError, setEraseError] = useState("");
  const [eraseBusy, setEraseBusy] = useState(false);

  // Keep the field in step with the account the session holds.
  useEffect(() => {
    if (account) setDisplayName(account.displayName);
  }, [account]);

  if (!ready) return null;
  if (!account) return <Navigate to="/login" replace />;

  const trimmed = displayName.trim();
  const canSaveProfile = !profileBusy && trimmed !== "" && trimmed !== account.displayName;

  const tooShort = newPassword.length > 0 && newPassword.length < PASSWORD_MIN;
  const canChangePassword =
    !passwordBusy &&
    currentPassword !== "" &&
    !tooShort &&
    newPassword === confirmPassword;

  async function saveProfile(event: FormEvent) {
    event.preventDefault();
    setProfileBusy(true);
    setProfileError("");
    setProfileMessage("");
    try {
      // The server decides what is stored; the session is updated from its answer, not from
      // the value typed here, so a rejected or trimmed value cannot linger in the UI.
      updateAccount(
        await api<Account>("/account", {
          method: "PUT",
          body: JSON.stringify({ displayName }),
        }),
      );
      setProfileMessage("资料已保存。");
    } catch (e) {
      setProfileError((e as Error).message);
    } finally {
      setProfileBusy(false);
    }
  }

  async function changePassword(event: FormEvent) {
    event.preventDefault();
    setPasswordBusy(true);
    setPasswordError("");
    try {
      await api("/account/password", {
        method: "POST",
        body: JSON.stringify({ currentPassword, newPassword }),
      });
      // The change invalidated every token for this account, including the one in this browser,
      // so there is nothing left to stay signed in with. Return to sign-in with the reason.
      await signOut();
      navigate("/login", { state: { notice: "密码已更新，请用新密码重新登录。" } });
    } catch (e) {
      setPasswordError((e as Error).message);
      setPasswordBusy(false);
    }
  }

  async function logoutEverywhere() {
    setPasswordBusy(true);
    setPasswordError("");
    try {
      await api("/account/logout-all", { method: "POST" });
    } catch (e) {
      setPasswordError((e as Error).message);
      setPasswordBusy(false);
      return;
    }
    await signOut();
    navigate("/login", { state: { notice: "已退出所有设备，请重新登录。" } });
  }

  async function exportData() {
    setExportBusy(true);
    setExportError("");
    setExportMessage("");
    try {
      // The server returns the document in the shared envelope; saving it is the client's job.
      const data = await api<unknown>("/account/export");
      const blob = new Blob([JSON.stringify(data, null, 2)], {
        type: "application/json",
      });
      const url = URL.createObjectURL(blob);
      const link = document.createElement("a");
      link.href = url;
      link.download = `澄镜-个人数据-${new Date().toISOString().slice(0, 10)}.json`;
      link.click();
      setTimeout(() => URL.revokeObjectURL(url), 1000);
      setExportMessage("已开始下载。文件含你的个人资料，请保存在你信任的位置。");
    } catch (e) {
      setExportError((e as Error).message);
    } finally {
      setExportBusy(false);
    }
  }

  async function eraseAccount(event: FormEvent) {
    event.preventDefault();
    setEraseBusy(true);
    setEraseError("");
    try {
      await api("/account", {
        method: "DELETE",
        body: JSON.stringify({
          currentPassword: erasePassword,
          confirmation: eraseConfirm,
        }),
      });
      // The account is gone; drop the local session and say so on the way out.
      await signOut();
      navigate("/login", {
        state: { notice: "账号已注销，活跃存储中的账号数据已清除。" },
      });
    } catch (e) {
      setEraseError((e as Error).message);
      setEraseBusy(false);
    }
  }

  return (
    <div className="id-stack">
      <section className="id-card id-account">
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

        <form className="id-form" onSubmit={saveProfile} noValidate>
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
          {profileMessage && (
            <p className="id-notice" role="status">
              {profileMessage}
            </p>
          )}
          {profileError && (
            <p className="id-error" role="alert">
              {profileError}
            </p>
          )}
          <button className="id-button primary" disabled={!canSaveProfile} type="submit">
            {profileBusy ? "正在保存…" : "保存资料"}
          </button>
        </form>
      </section>

      <section className="id-card id-account">
        <span className="id-eyebrow">CHENGJING · 我的数据</span>
        <h2 className="id-section-title">导出我的数据</h2>
        <p className="id-lede">
          下载一份属于你自己的副本，包含账号资料。导出文件里不含密码、登录令牌或模型密钥。
        </p>
        <p className="id-fineprint id-fineprint-block">
          准备计划、面试记录与评分由其他模块保存，本轮尚未接入导出；文件里的
          <code> notIncludedYet </code>
          会列出当前未包含的部分，不会假装完整。
        </p>
        {exportMessage && (
          <p className="id-notice" role="status">
            {exportMessage}
          </p>
        )}
        {exportError && (
          <p className="id-error" role="alert">
            {exportError}
          </p>
        )}
        <button
          className="id-button"
          type="button"
          disabled={exportBusy}
          onClick={() => void exportData()}
        >
          {exportBusy ? "正在准备…" : "导出我的数据"}
        </button>
      </section>

      <section className="id-card id-account">
        <span className="id-eyebrow">CHENGJING · 登录安全</span>
        <h2 className="id-section-title">修改密码</h2>
        <p className="id-lede">
          修改后，所有设备上的登录都会失效，需要重新登录。
        </p>

        <form className="id-form" onSubmit={changePassword} noValidate>
          <label className="id-field">
            <span>当前密码</span>
            <input
              type="password"
              value={currentPassword}
              onChange={(e) => setCurrentPassword(e.target.value)}
              autoComplete="current-password"
              required
            />
          </label>
          <label className="id-field">
            <span>新密码</span>
            <input
              type="password"
              value={newPassword}
              onChange={(e) => setNewPassword(e.target.value)}
              maxLength={PASSWORD_MAX}
              autoComplete="new-password"
              aria-describedby="id-new-password-rule"
              required
            />
          </label>
          <label className="id-field">
            <span>再次输入新密码</span>
            <input
              type="password"
              value={confirmPassword}
              onChange={(e) => setConfirmPassword(e.target.value)}
              maxLength={PASSWORD_MAX}
              autoComplete="new-password"
              required
            />
          </label>
          <p
            id="id-new-password-rule"
            className={tooShort ? "id-hint invalid" : "id-hint"}
          >
            {PASSWORD_MIN}—{PASSWORD_MAX} 位。多字节字符请适当缩短。
          </p>
          {confirmPassword !== "" && confirmPassword !== newPassword && (
            <p className="id-hint invalid">两次输入的新密码不一致。</p>
          )}
          {passwordError && (
            <p className="id-error" role="alert">
              {passwordError}
            </p>
          )}
          <button className="id-button primary" disabled={!canChangePassword} type="submit">
            {passwordBusy ? "正在处理…" : "修改密码并重新登录"}
          </button>
        </form>

        <div className="id-divider" />

        <h2 className="id-section-title">退出所有设备</h2>
        <p className="id-lede">
          如果你怀疑账号在其他设备上仍处于登录状态，可以一次全部退出。密码不会改变。
        </p>
        <button
          className="id-button"
          type="button"
          disabled={passwordBusy}
          onClick={() => void logoutEverywhere()}
        >
          退出所有设备
        </button>
      </section>

      <section className="id-card id-account id-danger">
        <span className="id-eyebrow">CHENGJING · 注销账号</span>
        <h2 className="id-section-title">注销我的账号</h2>
        <p className="id-lede">
          清除活跃存储中的账号数据，并立即结束所有设备上的登录。此操作不可撤销。
          建议先导出你的数据。
        </p>
        <p className="id-fineprint id-fineprint-block">
          此前产生的备份由部署方按其保留周期到期清理，备份中的数据不会出现在任何接口里。
        </p>

        {eraseOpen ? (
          <form className="id-form" onSubmit={eraseAccount} noValidate>
            <label className="id-field">
              <span>当前密码</span>
              <input
                type="password"
                value={erasePassword}
                onChange={(e) => setErasePassword(e.target.value)}
                autoComplete="current-password"
                required
              />
            </label>
            <label className="id-field">
              <span>
                输入「{ERASE_PHRASE}」以确认
              </span>
              <input
                value={eraseConfirm}
                onChange={(e) => setEraseConfirm(e.target.value)}
                required
              />
            </label>
            {eraseError && (
              <p className="id-error" role="alert">
                {eraseError}
              </p>
            )}
            <div className="id-button-row">
              <button
                className="id-button"
                type="button"
                onClick={() => {
                  setEraseOpen(false);
                  setEraseError("");
                }}
              >
                取消
              </button>
              <button
                className="id-button danger"
                type="submit"
                disabled={
                  eraseBusy || eraseConfirm !== ERASE_PHRASE || erasePassword === ""
                }
              >
                {eraseBusy ? "正在注销…" : "确认注销并删除"}
              </button>
            </div>
          </form>
        ) : (
          <button
            className="id-button danger-outline"
            type="button"
            onClick={() => setEraseOpen(true)}
          >
            查看注销确认
          </button>
        )}
      </section>

      <p className="id-fineprint id-stack-foot">
        <Link to="/">← 返回工作台</Link>
      </p>
    </div>
  );
}
