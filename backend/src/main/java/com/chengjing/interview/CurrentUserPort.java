package com.chengjing.interview;

/**
 * Owner: member C (interview module).
 *
 * Narrow port for "who is asking". Member A owns authentication and the account id is the owner key
 * for every personal record (module contract §3). Until A02 lands, the adapter in this package
 * resolves the caller from a request header; replacing that single adapter is the only change needed
 * once A02 is merged, and no other interview class has to learn about tokens.
 */
public interface CurrentUserPort {

    /** @return the caller's account id, or throws {@link InterviewException#unauthorized} when absent. */
    String requireUserId();
}
