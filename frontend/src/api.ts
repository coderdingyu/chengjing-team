export type ApiResponse<T> = { success: boolean; data: T; message: string };

const TOKEN_KEY = "chengjing.token";

/** The bearer token for the signed-in session, or "" when nobody is signed in. */
export function token(): string {
  return localStorage.getItem(TOKEN_KEY) ?? "";
}

export function setToken(value: string) {
  localStorage.setItem(TOKEN_KEY, value);
}

export function clearToken() {
  localStorage.removeItem(TOKEN_KEY);
}

export async function api<T>(path: string, init?: RequestInit): Promise<T> {
  const headers = new Headers(init?.headers);
  if (!headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }
  // Attached here rather than per call site, so no module can forget it on a protected endpoint.
  const bearer = token();
  if (bearer) {
    headers.set("Authorization", `Bearer ${bearer}`);
  }
  const response = await fetch(`/api/v1${path}`, { ...init, headers });
  const body = (await response.json()) as ApiResponse<T>;
  if (!response.ok || !body.success)
    throw new Error(body.message || "请求失败");
  return body.data;
}
