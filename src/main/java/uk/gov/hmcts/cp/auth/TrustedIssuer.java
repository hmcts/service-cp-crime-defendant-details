package uk.gov.hmcts.cp.auth;

import java.util.Set;

/** One accepted Entra issuer. A token is only ever checked against the issuer its own {@code iss} names. */
public record TrustedIssuer(String name, String issuer, String tenantId, Set<String> audiences, String jwksUri) {

    public TrustedIssuer {
        audiences = Set.copyOf(audiences);
    }
}
