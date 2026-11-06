package uk.gov.hmcts.cp.auth;

import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;

/** Signing keys for one issuer's JWKS URI; each issuer gets its own source. */
@FunctionalInterface
public interface JwkSourceFactory {

    JWKSource<SecurityContext> forJwksUri(String jwksUri);
}
