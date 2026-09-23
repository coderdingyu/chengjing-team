export type ApiResponse<T> = { success: boolean; data: T; message: string };

export async function api<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`/api/v1${path}`, {
    ...init,
    headers: { "Content-Type": "application/json", ...init?.headers },
  });
  const body = (await response.json()) as ApiResponse<T>;
  if (!response.ok || !body.success)
    throw new Error(body.message || "请求失败");
  return body.data;
}
