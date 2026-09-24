package com.chengjing.identity;

/**
 * The authenticated caller, as the rest of the system sees them.
 *
 * <p>This is the DTO other modules receive (docs/模块契约.md §3): B/C/D/E store
 * {@link #id()} as the owner key on every personal record and check it server-side. It carries no
 * credential — never hand a token or a password hash across a module boundary.
 */
public record AuthUser(String id, String email, String displayName) {
}
