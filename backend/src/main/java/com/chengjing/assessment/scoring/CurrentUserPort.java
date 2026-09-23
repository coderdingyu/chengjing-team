package com.chengjing.assessment.scoring;

/** Adapter point for member A's authenticated current-user context. */
public interface CurrentUserPort {
    String requireUserId();
}
