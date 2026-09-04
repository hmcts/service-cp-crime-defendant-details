package uk.gov.hmcts.cp.auth;

import java.util.List;
import java.util.UUID;

/**
 * The identity established from a validated app-only access token.
 */
public record ValidatedCaller(UUID clientId, List<String> roles) {

    public ValidatedCaller {
        roles = List.copyOf(roles);
    }
}
