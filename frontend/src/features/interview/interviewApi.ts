import { api } from "../../api";

/**
 * Owner: member C (interview feature).
 *
 * The shared `api()` helper stays untouched; this wrapper only adds the request headers the
 * interview module needs, so the module can keep its own transport concerns in its own folder.
 */

const OWNER_KEY = "chengjing-interview-owner";
const OWNER_SHAPE = /^[A-Za-z0-9_-]{6,64}$/;

/**
 * Owner key used while member A's login (A02) is still an open PR.
 *
 * It is kept in localStorage so a refresh — including the "continue my room" flow — resolves to the
 * same owner, and it is replaced by the authenticated principal as soon as A02 lands.
 */
export function ownerId(): string {
  try {
    const saved = localStorage.getItem(OWNER_KEY);
    if (saved && OWNER_SHAPE.test(saved)) return saved;
  } catch {
    // storage can be unavailable in private mode; fall through to a per-session id
  }
  const generated = `u${Date.now().toString(36)}${Math.random().toString(36).slice(2, 10)}`;
  try {
    localStorage.setItem(OWNER_KEY, generated);
  } catch {
    // ignore: the id only has to stay stable for this page's lifetime
  }
  return generated;
}

function headers(): Record<string, string> {
  return { "X-Chengjing-User": ownerId() };
}

export const interviewApi = {
  get: <T,>(path: string) => api<T>(path, { headers: headers() }),
  post: <T,>(path: string, body?: unknown) =>
    api<T>(path, { method: "POST", headers: headers(), body: JSON.stringify(body ?? {}) }),
  put: <T,>(path: string, body?: unknown) =>
    api<T>(path, { method: "PUT", headers: headers(), body: JSON.stringify(body ?? {}) }),
};
