package com.chengjing.preparation;

import java.util.List;

/**
 * 测试支持：B 模块的数据目前存在进程内存里，同一个 Spring 上下文会被多个用例复用。
 * 每个用例开始前清空测试用账号的数据，让「列表里有几条」这类断言不受执行顺序影响。
 */
final class PreparationTestStore {
    /** 测试里出现过的账号。 */
    private static final List<String> ACCOUNTS = List.of("candidate-1", "candidate-2");

    private PreparationTestStore() {}

    static void reset(PreparationStore store) {
        for (String userId : ACCOUNTS) {
            for (PreparationStore.Kind kind : PreparationStore.Kind.values()) {
                for (PreparationStore.Entry entry : store.list(userId, kind)) {
                    store.delete(userId, entry.id(), kind);
                }
            }
        }
    }
}
