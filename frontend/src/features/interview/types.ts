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
  questionId: string;
  stage: InterviewStageView;
  turns: InterviewTurnView[];
  answers: InterviewAnswerView[];
  createdAt: string;
};

export type InterviewTurnView = {
  id: string;
  seq: number;
  questionId: string;
  role: string;
  text: string;
  intent: string;
  at: string;
};

export type InterviewAnswerView = {
  id: string;
  questionId: string;
  question: string;
  text: string;
  intent: string;
  source: string;
  answeredAt: string;
  gradeStatus: string;
};

export type InterviewTurnResultView = {
  turn: InterviewTurnView;
  dialogueSource: string;
  answers: InterviewAnswerView[];
  transcript: InterviewTurnView[];
  stageIndex: number;
  totalStages: number;
  status: string;
};

/** A submission id that is stable across a retry of the same text. */
export function newRequestId(): string {
  return `r${Date.now().toString(36)}${Math.random().toString(36).slice(2, 10)}`;
}

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

export const GRADE_LABEL: Record<string, string> = {
  PENDING: "待评分",
  COMPLETED: "已评分",
  FAILED: "评分失败",
};

export function statusLabel(status: string): string {
  return STATUS_LABEL[status] ?? status;
}

export function gradeLabel(status: string): string {
  return GRADE_LABEL[status] ?? status;
}

export function formatClock(totalSeconds: number): string {
  const safe = Math.max(0, Math.floor(totalSeconds));
  const minutes = Math.floor(safe / 60).toString().padStart(2, "0");
  const seconds = (safe % 60).toString().padStart(2, "0");
  return `${minutes}:${seconds}`;
}
