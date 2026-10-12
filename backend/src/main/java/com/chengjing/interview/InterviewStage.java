package com.chengjing.interview;

/**
 * Owner: member C (interview module).
 *
 * One planned stage of a room: the label shown in the progress bar, the question the interviewer
 * asks, and the optional material the candidate may ask about. Stage decks are generated per room so
 * a later swap to member B's case catalog only changes {@link InterviewCatalog}.
 */
public record InterviewStage(String title, String prompt, String material) {

    public InterviewStage {
        title = title == null ? "" : title;
        prompt = prompt == null ? "" : prompt;
        material = material == null ? "" : material;
    }
}
