package uk.gov.hmcts.cp.auth;

/**
 * The bearer token error codes defined by RFC 6750 section 3.1.
 *
 * <p>Held here rather than on {@link TokenRejectionReason} because an enum's own static fields are
 * initialised after its constants, so the constants could not reference them.
 */
final class Rfc6750 {

    /* default */ static final String INVALID_REQUEST = "invalid_request";
    /* default */ static final String INVALID_TOKEN = "invalid_token";
    /* default */ static final String INSUFFICIENT_SCOPE = "insufficient_scope";

    private Rfc6750() {
    }
}
