package com.chengjing.preparation;

import java.util.ArrayList;
import java.util.List;

/**
 * 一道单题专项练习：题目来自简历深挖节点，草稿与提交过的回答分开保存。
 * 草稿随时可以写；提交失败时草稿不会被清空，本人可以接着改、接着提交。
 */
public record PracticeSession(
        String planId,
        String nodeId,
        String nodeTitle,
        String questionId,
        String question,
        String draft,
        List<PracticeAnswer> answers
) {
    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_ANSWERED = "ANSWERED";

    /** 一次都没提交过就是草稿中，提交过就记为已回答；重答只是再加一次 revision。 */
    public String status() {
        return answers.isEmpty() ? STATUS_DRAFT : STATUS_ANSWERED;
    }

    public PracticeSession withDraft(String nextDraft) {
        return new PracticeSession(planId, nodeId, nodeTitle, questionId, question, nextDraft, answers);
    }

    /** 收下这份回答，同时清空草稿：草稿只服务于还没交出去的那一份。 */
    public PracticeSession withAnswer(PracticeAnswer answer) {
        List<PracticeAnswer> next = new ArrayList<>(answers);
        next.add(answer);
        return new PracticeSession(planId, nodeId, nodeTitle, questionId, question, "", List.copyOf(next));
    }
}
