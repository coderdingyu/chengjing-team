/** Shared shapes for the interview feature. Mirrors the JSON returned by /api/v1/interviews. */

export type InterviewStageView = {
  title: string;
  prompt: string;
  material: string;
};

export type InterviewRoomView = {
  id: string;
  title: string;
  role: string;
  level: string;
  minutes: number;
  status: string;
  stageIndex: number;
  totalStages: number;
  stage: InterviewStageView;
  createdAt: string;
};

export type InterviewCatalogView = {
  roles: string[];
  levels: string[];
  durations: number[];
};

export const STATUS_LABEL: Record<string, string> = {
  ACTIVE: "进行中",
  PAUSED: "已暂停",
  COMPLETED: "已结束",
};

export function statusLabel(status: string): string {
  return STATUS_LABEL[status] ?? status;
}

export function formatClock(totalSeconds: number): string {
  const safe = Math.max(0, Math.floor(totalSeconds));
  const minutes = Math.floor(safe / 60).toString().padStart(2, "0");
  const seconds = (safe % 60).toString().padStart(2, "0");
  return `${minutes}:${seconds}`;
}
