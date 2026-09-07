package uk.gov.hmcts.cp.auth;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.mock.env.MockEnvironment;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Mints access tokens against a key set this test controls, so that signature, issuer, audience,
 * expiry and every claim rule run for real rather than being stubbed.
 */
public final class TestTokens {

    public static final String TENANT_ID = "11111111-1111-1111-1111-111111111111";
    public static final String AUDIENCE = "22222222-2222-2222-2222-222222222222";
    public static final String ISSUER = "https://login.microsoftonline.com/" + TENANT_ID + "/v2.0";
    public static final String REQUIRED_ROLE = "DefendantDetails.Read";
    public static final long CLOCK_SKEW_SECONDS = 60;

    /** The calling application's client id - the {@code azp} claim, and the caller's identity. */
    public static final UUID CLIENT_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    /**
     * The service principal object id for that application in the tenant - the {@code oid} and
     * {@code sub} claims. Deliberately different from {@link #CLIENT_ID}, so that a validator
     * taking the identity from the wrong claim fails a test rather than passing by coincidence.
     */
    public static final String OBJECT_ID = "44444444-4444-4444-4444-444444444444";

    /** A sibling CP API's audience - the GUID that gets circulated during a spike. */
    public static final String SIBLING_API_AUDIENCE = "55555555-5555-5555-5555-555555555555";

    public static final String GRAPH_AUDIENCE = "https://graph.microsoft.com";

    /** The key the JWKS publishes, and the one Entra would sign with. */
    public static final RSAKey SIGNING_KEY = generateKey("test-signing-key");

    /** A key that is not in the JWKS at all. */
    public static final RSAKey UNRELATED_KEY = generateKey("unrelated-key");

    private TestTokens() {
    }

    /** The key set the validator is configured with. Publishes only {@link #SIGNING_KEY}. */
    public static JWKSource<SecurityContext> jwkSource() {
        return new ImmutableJWKSet<>(new JWKSet(SIGNING_KEY.toPublicJWK()));
    }

    /** A validator wired to that key set and to the properties below. */
    public static EntraTokenValidator validator() {
        return new EntraTokenValidator(properties(AuthMode.ENFORCE), jwkSource());
    }

    public static AuthProperties properties(final AuthMode mode) {
        final MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("test");
        return new AuthProperties(environment, mode, TENANT_ID, AUDIENCE, "", "",
                REQUIRED_ROLE, CLOCK_SKEW_SECONDS, 300);
    }

    /**
     * A well-formed Entra v2.0 app-only token: {@code sub == oid}, roles present, no {@code scp},
     * and no {@code idtyp}.
     */
    public static JWTClaimsSet.Builder validClaims() {
        final Instant now = Instant.now();
        return new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .audience(AUDIENCE)
                .subject(OBJECT_ID)
                .claim("oid", OBJECT_ID)
                .claim("azp", CLIENT_ID.toString())
                .claim("tid", TENANT_ID)
                .claim("ver", "2.0")
                .claim("roles", List.of(REQUIRED_ROLE))
                .issueTime(Date.from(now))
                .notBeforeTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(3600)));
    }

    public static String validToken() {
        return sign(validClaims().build());
    }

    public static String sign(final JWTClaimsSet claims) {
        return sign(header(SIGNING_KEY.getKeyID()).build(), claims, SIGNING_KEY);
    }

    public static String sign(final JWSHeader header, final JWTClaimsSet claims, final RSAKey key) {
        try {
            final SignedJWT jwt = new SignedJWT(header, claims);
            jwt.sign(new RSASSASigner(key));
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException("Could not sign the test token", e);
        }
    }

    public static JWSHeader.Builder header(final String keyId) {
        return new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(keyId);
    }

    private static RSAKey generateKey(final String keyId) {
        try {
            return new RSAKeyGenerator(2048)
                    .keyID(keyId)
                    .keyUse(KeyUse.SIGNATURE)
                    .generate();
        } catch (JOSEException e) {
            throw new IllegalStateException("Could not generate a test signing key", e);
        }
    }
}
