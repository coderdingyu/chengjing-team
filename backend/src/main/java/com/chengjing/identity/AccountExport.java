package com.chengjing.identity;

import java.time.Instant;
import java.util.List;

/**
 * A copy of the account holder's own data (FR-A05).
 *
 * <p>An explicit allow-list of fields, like {@link AccountView}: the domain object holds a password
 * hash, and an export is a file that will sit in someone's downloads folder, so what goes in is
 * named here rather than inherited from whatever the account happens to contain. No credential —
 * no password hash, no token — can reach it, and docs/需求分析说明书.md §8 requires that.
 *
 * <p>{@code notIncludedYet} names the parts of a complete export that this round does not produce
 * yet, because they belong to modules that have not landed. It is here so the file does not look
 * complete when it is not: a download that silently omits someone's practice history is worse than
 * one that says the history is not available yet.
 */
public record AccountExport(
        int formatVersion,
        Instant exportedAt,
        AccountView account,
        List<String> notIncludedYet) {

    /** Bump when the shape changes so a reader can tell two exports apart. */
    public static final int CURRENT_FORMAT_VERSION = 1;

    private static final List<String> PENDING = List.of(
            "准备计划与经历素材（面试准备模块，尚未合并）",
            "面试房间与文字回答（面试进行模块，尚未合并）",
            "评分、复盘与成长记录（评分与成长模块，尚未合并）",
            "模型供应商配置与密钥（模型与平台模块，尚未合并；密钥本身不会出现在导出中）");

    public static AccountExport of(User account, Instant exportedAt) {
        return new AccountExport(
                CURRENT_FORMAT_VERSION, exportedAt, AccountView.of(account), PENDING);
    }
}
