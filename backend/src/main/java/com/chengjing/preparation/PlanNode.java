package com.chengjing.preparation;

import java.time.Instant;

/** 简历深挖节点：把一段经历拆成可以分别讲述和验证的几段。 */
public record PlanNode(
        String id,
        String kind,
        String title,
        String prompt,
        String status,
        String content,
        Instant updatedAt
) {
    public static final String STATUS_TODO = "TODO";
    public static final String STATUS_DRAFTING = "DRAFTING";
    public static final String STATUS_DONE = "DONE";

    public PlanNode withStatus(String nextStatus, Instant at) {
        return new PlanNode(id, kind, title, prompt, nextStatus, content, at);
    }

    public PlanNode withContent(String nextContent, String nextStatus, Instant at) {
        return new PlanNode(id, kind, title, prompt, nextStatus, nextContent, at);
    }
}
