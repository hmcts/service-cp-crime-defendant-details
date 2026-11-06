package uk.gov.hmcts.cp.auth;

import java.util.List;
import java.util.UUID;

/**
 * The identity established from a validated app-only access token.
 *
 * @param issuerName the {@link TrustedIssuer} that validated the token, for metrics only
 */
public record ValidatedCaller(UUID clientId, List<String> roles, String issuerName) {

    public ValidatedCaller {
        roles = List.copyOf(roles);
    }
}
