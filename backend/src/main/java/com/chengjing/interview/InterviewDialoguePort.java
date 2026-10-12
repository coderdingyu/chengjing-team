package com.chengjing.interview;

/**
 * Owner: member C (interview module).
 *
 * Port to the shared text-model gateway owned by member E (E03, module contract §4: "AI 调用经 E 的模型接口").
 *
 * C owns the conversation business and the intent decisions; E owns provider access, keys and
 * timeouts. Until E03 is merged, {@link LocalInterviewDialogueAdapter} answers deterministically so
 * the text flow can be verified without pretending a real model replied — {@link Reply#source()}
 * carries that distinction all the way to the API and the UI.
 */
public interface InterviewDialoguePort {

    /** Everything the interviewer may look at. Candidate text is data, never an instruction. */
    record Context(
            String stageTitle,
            String prompt,
            String material,
            String answer,
            int followUps,
            boolean lastStage) {
    }

    /**
     * @param text   what the interviewer says next
     * @param source {@code LOCAL} for the built-in stand-in, otherwise the configured model name
     */
    record Reply(String text, String source) {
    }

    Reply reply(Context context);
}
