package uk.gov.hmcts.cp.auth;

/**
 * Rollout state for Entra access token validation.
 *
 * <p>{@link #OFF} and {@link #OBSERVE} provide no protection and are rejected at startup unless
 * the {@code local} or {@code test} profile is active. See {@link AuthProperties}.
 */
public enum AuthMode {

    /** No validation. The caller is never identified. */
    OFF,

    /**
     * Tokens are fully validated and every failure is logged and counted, but no request is
     * rejected. A diagnostic for finding broken clients before enforcing - not a protection.
     */
    OBSERVE,

    /** Tokens are fully validated and invalid requests are rejected. */
    ENFORCE
}
