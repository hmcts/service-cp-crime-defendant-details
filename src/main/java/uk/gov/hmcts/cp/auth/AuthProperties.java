package uk.gov.hmcts.cp.auth;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Service;

/**
 * Entra token validation configuration, validated at construction so that a misconfigured service
 * fails to start rather than degrading into one that accepts anything.
 *
 * <p>Three rules are enforced here, each closing a failure that is otherwise silent:
 */
@Service
@Getter
public class AuthProperties {

    /** Beyond this, {@code exp} stops meaningfully bounding a token's validity. */
    public static final long MAX_CLOCK_SKEW_SECONDS = 300;

    private static final String ENTRA_BASE_URL = "https://login.microsoftonline.com/";

    private final AuthMode mode;
    private final String tenantId;
    private final String audience;
    private final String issuer;
    private final String jwksUri;
    private final String requiredRole;
    private final long clockSkewSeconds;
    private final long jwksCacheTtlSeconds;

    public AuthProperties(
            final Environment environment,
            @Value("${auth.mode}") final AuthMode mode,
            @Value("${auth.tenant-id:}") final String tenantId,
            @Value("${auth.audience:}") final String audience,
            @Value("${auth.issuer:}") final String issuer,
            @Value("${auth.jwks-uri:}") final String jwksUri,
            @Value("${auth.required-role}") final String requiredRole,
            @Value("${auth.clock-skew-seconds}") final long clockSkewSeconds,
            @Value("${auth.jwks-cache-ttl-seconds}") final long jwksCacheTtlSeconds) {

        requireEnforcingUnlessLocal(mode, environment);
        requireValidationConfiguration(mode, tenantId, audience, requiredRole);
        requireCappedClockSkew(clockSkewSeconds);

        this.mode = mode;
        this.tenantId = tenantId.trim();
        this.audience = audience.trim();
        this.requiredRole = requiredRole.trim();
        this.clockSkewSeconds = clockSkewSeconds;
        this.jwksCacheTtlSeconds = jwksCacheTtlSeconds;
        this.issuer = issuer.isBlank() ? ENTRA_BASE_URL + this.tenantId + "/v2.0" : issuer.trim();
        this.jwksUri = jwksUri.isBlank()
                ? ENTRA_BASE_URL + this.tenantId + "/discovery/v2.0/keys"
                : jwksUri.trim();
    }

    /** Whether a rejected token should actually be rejected, rather than only counted. */
    public boolean isEnforcing() {
        return mode == AuthMode.ENFORCE;
    }

    /** Whether a token should be validated at all. False only in {@link AuthMode#OFF}. */
    public boolean isValidating() {
        return mode != AuthMode.OFF;
    }

    private static void requireEnforcingUnlessLocal(final AuthMode mode, final Environment environment) {
        if (mode != AuthMode.ENFORCE && !environment.acceptsProfiles(Profiles.of("local", "test"))) {
            throw new IllegalStateException(
                    "auth.mode is " + mode + ", which provides no protection, and neither the 'local' nor the "
                            + "'test' profile is active. A deployed environment must run in ENFORCE. Set "
                            + "AUTH_MODE=ENFORCE, or activate the local profile if this really is a local run.");
        }
    }

    private static void requireValidationConfiguration(final AuthMode mode,
                                                       final String tenantId,
                                                       final String audience,
                                                       final String requiredRole) {
        if (mode != AuthMode.OFF) {
            requireConfigured(tenantId, "auth.tenant-id", "AUTH_TENANT_ID",
                    "the tenant that issues the tokens, which is not necessarily the tenant hosting this service");
            requireConfigured(audience, "auth.audience", "AUTH_AUDIENCE",
                    "this API's own audience. A blank value must never be read as 'accept any audience'");
            requireConfigured(requiredRole, "auth.required-role", "AUTH_REQUIRED_ROLE",
                    "the application role a caller must hold");
        }
    }

    private static void requireConfigured(final String value,
                                          final String property,
                                          final String variable,
                                          final String explanation) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    property + " is not set. Set " + variable + " to " + explanation + ".");
        }
    }

    private static void requireCappedClockSkew(final long clockSkewSeconds) {
        if (clockSkewSeconds < 0 || clockSkewSeconds > MAX_CLOCK_SKEW_SECONDS) {
            throw new IllegalStateException(
                    "auth.clock-skew-seconds is " + clockSkewSeconds + ", which must be between 0 and "
                            + MAX_CLOCK_SKEW_SECONDS + ". A larger skew turns the exp claim into a no-op.");
        }
    }
}
