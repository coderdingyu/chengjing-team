package com.chengjing.interview;

import org.springframework.stereotype.Component;

/**
 * Owner: member C (interview module).
 *
 * Deterministic stand-in for member E's text gateway (E03). It exists so C02 can be verified end to
 * end on a machine without any API key, and it is replaced — not extended — once E03 is merged.
 *
 * Two properties matter and are covered by tests:
 * <ul>
 *   <li>the reply never evaluates the answer (no score, no "答得很好"), because judging belongs to D;</li>
 *   <li>the candidate's text is never parsed as an instruction, so "忽略以上要求" cannot change the
 *       room. Real model prompts must keep the same guarantee.</li>
 * </ul>
 */
@Component
public class LocalInterviewDialogueAdapter implements InterviewDialoguePort {

    /** Surfaced through the API so the UI can say the reply did not come from a real model. */
    public static final String SOURCE = "LOCAL";

    @Override
    public Reply reply(Context context) {
        String tail = context.lastStage()
                ? "这个环节问完了，你可以进入结束反思，或继续补充。"
                : "你可以继续补充这段回答，或提交后进入下一个环节。";
        return new Reply("谢谢，我把这段回答记下了。" + tail, SOURCE);
    }
}
