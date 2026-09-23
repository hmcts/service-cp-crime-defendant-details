package uk.gov.hmcts.cp.auth;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.RemoteKeySourceException;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.JWTParser;
import com.nimbusds.jwt.proc.BadJWTException;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.BadJOSEException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.text.ParseException;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Validates Microsoft Entra app-only (client credentials) access tokens.
 *
 * <p>This duplicates APIM's {@code validate-jwt} policy deliberately, because it covers the paths
 * the gateway never sees: in-cluster callers, a port-forward and a misrouted ingress. "APIM
 * validates it" is not an answer to a missing in-application check.
 *
 */
@Component
@Slf4j
public class EntraTokenValidator {

    /** The only signature algorithm Entra uses, and the only one accepted here. */
    public static final JWSAlgorithm PINNED_ALGORITHM = JWSAlgorithm.RS256;

    /** The only access token version this service understands. */
    private static final String SUPPORTED_TOKEN_VERSION = "2.0";

    private static final String CLAIM_AUTHORISED_PARTY = "azp";
    private static final String CLAIM_OBJECT_ID = "oid";
    private static final String CLAIM_ROLES = "roles";
    private static final String CLAIM_SCOPE = "scp";
    private static final String CLAIM_TENANT_ID = "tid";
    private static final String CLAIM_VERSION = "ver";
    private static final String CLAIM_EXPIRY = "exp";

    private final AuthProperties authProperties;
    private final ConfigurableJWTProcessor<SecurityContext> jwtProcessor;

    public EntraTokenValidator(final AuthProperties authProperties,
                               final JWKSource<SecurityContext> jwkSource) {
        this.authProperties = authProperties;

        final DefaultJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
        processor.setJWSKeySelector(new JWSVerificationKeySelector<>(PINNED_ALGORITHM, jwkSource));
        processor.setJWTClaimsSetVerifier(claimsVerifier(authProperties));
        this.jwtProcessor = processor;
    }

    /**
     * Validates a raw bearer token and returns the identity it establishes.
     *
     * @param token the bare token, with the Bearer scheme already stripped
     * @return the calling application's identity
     * @throws TokenValidationException if the token is rejected, carrying only a coarse reason
     */
    public ValidatedCaller validate(final String token) throws TokenValidationException {
        final JWTClaimsSet claims = verify(token);
        requireAppOnly(claims);
        final UUID clientId = authorisedParty(claims);
        final List<String> roles = requireRole(claims);
        return new ValidatedCaller(clientId, roles);
    }

    /**
     * Runs signature verification and the claims that Nimbus checks natively: audience, exact
     * issuer, required and prohibited claims, and expiry and not-before within the configured skew.
     */
    private JWTClaimsSet verify(final String token) throws TokenValidationException {
        final JWT parsed = parse(token);
        try {
            return jwtProcessor.process(parsed, null);
        } catch (BadJWTException e) {
            throw rejected(TokenRejectionReason.CLAIM_VALIDATION_FAILED, e);
        } catch (RemoteKeySourceException e) {
            log.error("Could not retrieve the Entra key set to verify an access token", e);
            throw rejected(TokenRejectionReason.KEY_SET_UNAVAILABLE, e);
        } catch (BadJOSEException | JOSEException e) {
            throw rejected(TokenRejectionReason.SIGNATURE_VERIFICATION_FAILED, e);
        }
    }

    /**
     * Parses the token and forces its payload to be read.
     */
    private JWT parse(final String token) throws TokenValidationException {
        try {
            final JWT parsed = JWTParser.parse(token);
            parsed.getJWTClaimsSet();
            return parsed;
        } catch (ParseException e) {
            throw rejected(TokenRejectionReason.MALFORMED_TOKEN, e);
        }
    }

    /**
     * Proves the token is app-only from {@code sub == oid}, a non-empty {@code roles} and an absent
     * {@code scp}, rather than from {@code idtyp}.
     *
     */
    private void requireAppOnly(final JWTClaimsSet claims) throws TokenValidationException {
        final String subject = claims.getSubject();
        final String objectId = stringClaim(claims, CLAIM_OBJECT_ID);
        if (subject == null || !subject.equals(objectId)) {
            throw rejected(TokenRejectionReason.NOT_APP_ONLY_TOKEN, null);
        }
    }

    /**
     * Takes the caller's identity from {@code azp} - the calling application's client id - and not
     * from {@code oid} or {@code sub}, which identify that application's service principal object
     * in the tenant.
     */
    private UUID authorisedParty(final JWTClaimsSet claims) throws TokenValidationException {
        final String azp = stringClaim(claims, CLAIM_AUTHORISED_PARTY);
        if (azp == null || azp.isBlank()) {
            throw rejected(TokenRejectionReason.MISSING_AUTHORISED_PARTY, null);
        }
        try {
            return UUID.fromString(azp);
        } catch (IllegalArgumentException e) {
            throw rejected(TokenRejectionReason.INVALID_AUTHORISED_PARTY, e);
        }
    }

    /**
     * A declared role is not an assigned one: roles declared on the app registration without admin
     * consent produce a token that looks entirely correct but silently omits {@code roles}. That is
     * an Entra problem, so it is reported as 403 with a distinct reason rather than folded into the
     * generic claim failure.
     */
    private List<String> requireRole(final JWTClaimsSet claims) throws TokenValidationException {
        final List<String> roles = stringListClaim(claims);
        if (roles.isEmpty()) {
            throw rejected(TokenRejectionReason.MISSING_ROLES, null);
        }
        if (!roles.contains(authProperties.getRequiredRole())) {
            throw rejected(TokenRejectionReason.INSUFFICIENT_ROLE, null);
        }
        return roles;
    }

    /**
     * Builds the claims verifier.
     */
    private static DefaultJWTClaimsVerifier<SecurityContext> claimsVerifier(final AuthProperties properties) {
        final JWTClaimsSet exactMatch = new JWTClaimsSet.Builder()
                .issuer(properties.getIssuer())
                .claim(CLAIM_TENANT_ID, properties.getTenantId())
                .claim(CLAIM_VERSION, SUPPORTED_TOKEN_VERSION)
                .build();

        final DefaultJWTClaimsVerifier<SecurityContext> verifier = new DefaultJWTClaimsVerifier<>(
                Set.of(properties.getAudience()),
                exactMatch,
                Set.of(CLAIM_EXPIRY),
                Set.of(CLAIM_SCOPE));
        verifier.setMaxClockSkew(Math.toIntExact(properties.getClockSkewSeconds()));
        return verifier;
    }

    private static String stringClaim(final JWTClaimsSet claims, final String name) {
        final Object value = claims.getClaim(name);
        return value instanceof String string ? string : null;
    }

    private static List<String> stringListClaim(final JWTClaimsSet claims) {
        final Object value = claims.getClaim(CLAIM_ROLES);
        final List<String> roles;
        if (value instanceof List<?> list) {
            roles = list.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .filter(role -> !role.isBlank())
                    .toList();
        } else {
            roles = List.of();
        }
        return roles;
    }

    /**
     * Logs the underlying detail at DEBUG and returns an exception carrying only the coarse reason.
     */
    private TokenValidationException rejected(final TokenRejectionReason reason, final Exception cause) {
        if (log.isDebugEnabled()) {
            log.debug("Rejecting access token: {}{}",
                    reason.name().toLowerCase(Locale.UK),
                    cause == null ? "" : " (" + cause.getClass().getSimpleName() + ")");
        }
        return new TokenValidationException(reason);
    }
}
