package com.chengjing.interview;

/**
 * Owner: member C (interview module).
 *
 * C03 — the three in-interview requests a candidate may make, and the fixed interviewer response
 * for each. They are decided by this module because the module contract gives C the conversation
 * business and the intent decisions, while member E only owns model access.
 *
 * <p>None of the three is a formal answer: the room records them as conversation turns so the
 * transcript shows what happened, but no {@link InterviewAnswer} is created and member D therefore
 * never scores them. That is the C03 acceptance rule "澄清和重听不算作已评分回答".
 */
public final class InterviewIntent {

    /** "请把这道题的重点讲清楚。" — recorded, never scored. */
    public static final String CLARIFY = "CLARIFY";
    /** "请再念一遍题目。" — recorded, never scored. */
    public static final String REPLAY = "REPLAY";
    /** "给我一点思考时间。" — recorded, never scored. */
    public static final String THINK = "THINK";

    /** Surfaced through the API like the dialogue source, so the UI can label the reply. */
    public static final String SOURCE = "LOCAL";

    private InterviewIntent() {
    }

    public static boolean isKnown(String intent) {
        return CLARIFY.equals(intent) || REPLAY.equals(intent) || THINK.equals(intent);
    }

    /** What the transcript shows on the candidate's side. Kept short so it reads as a request. */
    public static String candidateLine(String intent) {
        return switch (intent) {
            case CLARIFY -> "（我想先澄清一下这道题的重点）";
            case REPLAY -> "（请再念一遍题目）";
            case THINK -> "（给我一点思考时间）";
            default -> "（请求）";
        };
    }

    /**
     * The interviewer's answer. Each intent gets a response that is unmistakably about that intent,
     * so the candidate can tell all three apart.
     */
    public static String reply(String intent, InterviewStage stage) {
        return switch (intent) {
            case CLARIFY -> "这道题来自「" + stage.title() + "」环节，重点看你的分析思路和取舍理由，"
                    + "不必背标准答案。你可以先说结论，再补一个亲身经历的例子。";
            case REPLAY -> "好的，我再说一遍题目：" + stage.prompt()
                    + (stage.material() == null || stage.material().isBlank()
                            ? "" : "（材料：" + stage.material() + "）");
            case THINK -> "没问题，你先想。想好后直接写下回答提交即可，思考时间不会打断本场面试。";
            default -> "好的。";
        };
    }
}
