package com.chengjing.identity;

import java.time.Instant;

/**
 * What the caller is told after erasing their account (FR-A06).
 *
 * <p>{@code backupNote} exists because erasure only reaches the active store. Anything already in
 * a backup keeps whatever retention period the deployment configured, and the person deleting their
 * account is entitled to know that rather than be told "deleted" and left to assume it is gone
 * everywhere. The retention period itself belongs to the deployment (E06).
 */
public record ErasureReceipt(Instant erasedAt, String backupNote) {

    static final String BACKUP_NOTE =
            "活跃存储中的账号数据已清除，所有设备上的登录已立即失效，"
                    + "邮箱与密码也已从账号记录中移除。"
                    + "此前产生的备份由部署方按其保留周期到期清理，备份中的数据不会出现在任何接口里；"
                    + "具体保留周期见部署文档（成员 E06）。";

    public static ErasureReceipt at(Instant erasedAt) {
        return new ErasureReceipt(erasedAt, BACKUP_NOTE);
    }
}
