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
  /** All stage titles in order, so the room can show the lineup (C05). */
  stages: string[];
  /** Seconds spent on the current stage, and the even split of the target duration for it. */
  stageElapsedSeconds: number;
  stageExpectedSeconds: number;
  isLastStage: boolean;
  /** Selected 3D interviewer and the catalog to switch between (C06). */
  avatar: string;
  avatars: InterviewAvatarView[];
  /** Whether the candidate agreed to open the camera. Never any video data. */
  videoConsent: boolean;
  turns: InterviewTurnView[];
  answers: InterviewAnswerView[];
  /** Unsent text kept on the server so a refresh restores it (C04). */
  draft: string;
  /** Seconds banked in the room; frozen while paused or finished. */
  elapsedSeconds: number;
  endedAt: string | null;
  createdAt: string;
};

/** One switchable 3D interviewer look (C06). */
export type InterviewAvatarView = {
  id: string;
  name: string;
  trait: string;
  accent: string;
  build: string;
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

/** Turn intents C02/C03 put on the transcript. Only ANSWER is a scored answer. */
export const INTENT_LABEL: Record<string, string> = {
  ANSWER: "",
  CLARIFY: "澄清",
  REPLAY: "重听",
  THINK: "思考",
  RESPOND: "",
};

/** The three in-interview requests of C03, in the order the room offers them. */
export const INTENT_ACTIONS = [
  { intent: "CLARIFY", label: "澄清问题", hint: "请面试官说明这道题的重点" },
  { intent: "REPLAY", label: "重听题目", hint: "请面试官再念一遍题目" },
  { intent: "THINK", label: "思考一下", hint: "需要一点思考时间" },
] as const;

export function statusLabel(status: string): string {
  return STATUS_LABEL[status] ?? status;
}

export function gradeLabel(status: string): string {
  return GRADE_LABEL[status] ?? status;
}

export function intentLabel(intent: string): string {
  return INTENT_LABEL[intent] ?? "";
}

export function formatClock(totalSeconds: number): string {
  const safe = Math.max(0, Math.floor(totalSeconds));
  const minutes = Math.floor(safe / 60).toString().padStart(2, "0");
  const seconds = (safe % 60).toString().padStart(2, "0");
  return `${minutes}:${seconds}`;
}
