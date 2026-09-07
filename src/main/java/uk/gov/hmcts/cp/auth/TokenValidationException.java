package uk.gov.hmcts.cp.auth;

import lombok.Getter;

import java.io.Serial;

/**
 * Thrown when an access token is rejected..
 *
 * <p>This exception carries a {@link TokenRejectionReason} and nothing else. It never holds the
 * raw token, and it never wraps the underlying library's exception as a cause: those messages
 * embed claim values taken from the token ("JWT audience rejected: [...]"), which would leak into
 * logs and error responses. The underlying detail is logged at DEBUG by the validator instead.
 */
@Getter
public class TokenValidationException extends Exception {

    @Serial
    private static final long serialVersionUID = 1L;

    private final transient TokenRejectionReason reason;

    public TokenValidationException(final TokenRejectionReason reason) {
        super(reason.getMessage(), null, false, false);
        this.reason = reason;
    }
}
