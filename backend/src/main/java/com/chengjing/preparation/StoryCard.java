package com.chengjing.preparation;

import java.util.List;

/**
 * 一条经历素材：要么是本人确认真实发生过的经历（SELF），要么是从公开岗位情境推演的模拟情境（SCENARIO）。
 * 模拟情境永远不能标成「本人确认的事实」，避免在面试里把演练内容当成真实经历讲出来。
 */
public record StoryCard(
        String title,
        String content,
        String source,
        boolean confirmedByOwner,
        List<String> tags,
        String role
) {
    /** 本人真实经历，需本人核对后确认。 */
    public static final String SOURCE_SELF = "SELF";
    /** 模拟情境：来自公开岗位案例的推演，不代表本人真实做过。 */
    public static final String SOURCE_SCENARIO = "SCENARIO";

    public StoryCard withBasics(String nextTitle, String nextContent, String nextSource,
            boolean nextConfirmed, List<String> nextTags, String nextRole) {
        return new StoryCard(nextTitle, nextContent, nextSource, nextConfirmed, nextTags, nextRole);
    }
}
