package uk.gov.hmcts.cp.auth;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * The coarse reason a token was rejected.
 */
@Getter
public enum TokenRejectionReason {

    MISSING_AUTHORIZATION_HEADER(HttpStatus.UNAUTHORIZED, null,
            "Access token is missing"),
    BLANK_AUTHORIZATION_HEADER(HttpStatus.UNAUTHORIZED, Rfc6750.INVALID_REQUEST,
            "Authorization header is blank"),
    UNSUPPORTED_AUTHORIZATION_SCHEME(HttpStatus.UNAUTHORIZED, Rfc6750.INVALID_REQUEST,
            "Authorization scheme is not Bearer"),
    EMPTY_BEARER_TOKEN(HttpStatus.UNAUTHORIZED, Rfc6750.INVALID_REQUEST,
            "Bearer token is empty"),
    MALFORMED_TOKEN(HttpStatus.UNAUTHORIZED, Rfc6750.INVALID_TOKEN,
            "Access token is malformed"),
    SIGNATURE_VERIFICATION_FAILED(HttpStatus.UNAUTHORIZED, Rfc6750.INVALID_TOKEN,
            "Access token signature is not valid"),
    CLAIM_VALIDATION_FAILED(HttpStatus.UNAUTHORIZED, Rfc6750.INVALID_TOKEN,
            "Access token claims are not valid"),
    MISSING_AUTHORISED_PARTY(HttpStatus.UNAUTHORIZED, Rfc6750.INVALID_TOKEN,
            "Access token does not identify the calling application"),
    INVALID_AUTHORISED_PARTY(HttpStatus.UNAUTHORIZED, Rfc6750.INVALID_TOKEN,
            "Access token does not identify the calling application"),
    NOT_APP_ONLY_TOKEN(HttpStatus.UNAUTHORIZED, Rfc6750.INVALID_TOKEN,
            "Access token is not an application-only token"),
    KEY_SET_UNAVAILABLE(HttpStatus.UNAUTHORIZED, Rfc6750.INVALID_TOKEN,
            "Access token could not be verified"),
    MISSING_ROLES(HttpStatus.FORBIDDEN, Rfc6750.INSUFFICIENT_SCOPE,
            "Access token grants no application roles"),
    INSUFFICIENT_ROLE(HttpStatus.FORBIDDEN, Rfc6750.INSUFFICIENT_SCOPE,
            "Access token does not grant the required application role");

    private final HttpStatus status;

    /**
     * The RFC 6750 error code, or {@code null} where the request carried no credentials at all -
     * RFC 6750 section 3 says the challenge then omits the error parameter.
     */
    private final String errorCode;

    private final String message;

    TokenRejectionReason(final HttpStatus status, final String errorCode, final String message) {
        this.status = status;
        this.errorCode = errorCode;
        this.message = message;
    }
}
