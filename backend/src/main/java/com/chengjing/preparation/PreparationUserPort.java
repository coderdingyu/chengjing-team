package com.chengjing.preparation;

/**
 * 成员 A 认证上下文的适配点：B 只取账号 ID 作为个人数据的归属键，
 * 不自行实现第二套身份验证，也不读取令牌内容。
 */
public interface PreparationUserPort {
    String requireUserId();
}
