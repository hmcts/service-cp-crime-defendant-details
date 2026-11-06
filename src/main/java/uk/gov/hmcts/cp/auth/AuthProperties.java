package uk.gov.hmcts.cp.auth;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Entra token validation configuration, validated at construction so that a misconfigured service
 * fails to start rather than degrading into one that accepts anything.
 *
 * <p>{@code auth.*} is the primary issuer (required). {@code auth.cp-*} is the CP issuer (optional);
 * blank means primary-issuer tokens only.
 *
 * <p>Three rules are enforced here, each closing a failure that is otherwise silent:
 */
@Service
@Getter
public class AuthProperties {

    /** Beyond this, {@code exp} stops meaningfully bounding a token's validity. */
    public static final long MAX_CLOCK_SKEW_SECONDS = 300;

    public static final String PRIMARY_ISSUER_NAME = "primary";
    public static final String CP_ISSUER_NAME = "cp";

    private static final String ENTRA_BASE_URL = "https://login.microsoftonline.com/";

    private final AuthMode mode;
    private final String tenantId;
    private final String audience;
    private final String issuer;
    private final String jwksUri;

    /**
     * CP issuing tenant. AMP-1386: CP tokens are accepted until consumers onboarded via CP APIM have
     * migrated; AMP-1387 removes this. Blank means CP tokens are rejected.
     */
    private final String cpTenantId;

    /** This API's own audience in the CP tenant. */
    private final String cpAudience;

    /** Derived from {@link #cpTenantId} when not set. */
    private final String cpIssuer;

    /** Derived from {@link #cpTenantId} when not set. */
    private final String cpJwksUri;

    private final Set<String> roles;
    private final long clockSkewSeconds;
    private final long jwksCacheTtlSeconds;

    /** Primary first, then CP when configured. */
    private final List<TrustedIssuer> trustedIssuers;

    public AuthProperties(
            final Environment environment,
            @Value("${auth.mode}") final AuthMode mode,
            @Value("${auth.tenant-id:}") final String tenantId,
            @Value("${auth.audience:}") final String audience,
            @Value("${auth.issuer:}") final String issuer,
            @Value("${auth.jwks-uri:}") final String jwksUri,
            @Value("${auth.cp-tenant-id:}") final String cpTenantId,
            @Value("${auth.cp-audience:}") final String cpAudience,
            @Value("${auth.cp-issuer:}") final String cpIssuer,
            @Value("${auth.cp-jwks-uri:}") final String cpJwksUri,
            @Value("${auth.roles:}") final String roles,
            @Value("${auth.clock-skew-seconds}") final long clockSkewSeconds,
            @Value("${auth.jwks-cache-ttl-seconds}") final long jwksCacheTtlSeconds) {

        final Set<String> parsedRoles = parseRoles(roles);
        requireEnforcingUnlessLocal(mode, environment);
        requireValidationConfiguration(mode, tenantId, audience, parsedRoles);
        requireCappedClockSkew(clockSkewSeconds);

        this.mode = mode;
        this.tenantId = normaliseId(tenantId);
        this.audience = audience.trim();
        this.roles = parsedRoles;
        this.clockSkewSeconds = clockSkewSeconds;
        this.jwksCacheTtlSeconds = jwksCacheTtlSeconds;
        this.issuer = issuer.isBlank() ? defaultIssuer(this.tenantId) : issuer.trim();
        this.jwksUri = jwksUri.isBlank() ? defaultJwksUri(this.tenantId) : jwksUri.trim();

        this.cpTenantId = normaliseId(cpTenantId);
        this.cpAudience = cpAudience.trim();
        this.cpIssuer = derivedOrBlank(cpIssuer, this.cpTenantId, true);
        this.cpJwksUri = derivedOrBlank(cpJwksUri, this.cpTenantId, false);

        requireCompleteCpPair();
        requireDistinctOrMergeableIssuers();
        this.trustedIssuers = buildTrustedIssuers();
    }

    /** Whether a rejected token should actually be rejected, rather than only counted. */
    public boolean isEnforcing() {
        return mode == AuthMode.ENFORCE;
    }

    /** Whether a token should be validated at all. False only in {@link AuthMode#OFF}. */
    public boolean isValidating() {
        return mode != AuthMode.OFF;
    }

    public final boolean isCpIssuerEnabled() {
        return !cpTenantId.isEmpty();
    }

    /** All-or-nothing, in every mode: a half-set CP pair is a misconfiguration, never silently ignored. */
    private void requireCompleteCpPair() {
        if (isCpAudienceMissing()) {
            throw new IllegalStateException(
                    "auth.cp-audience is not set. Set AUTH_CP_AUDIENCE whenever AUTH_CP_TENANT_ID is set.");
        }
        if (hasCpValuesWithoutTenant()) {
            throw new IllegalStateException(
                    "auth.cp-tenant-id is not set. Set AUTH_CP_TENANT_ID whenever any other AUTH_CP_* value is set.");
        }
    }

    private void requireDistinctOrMergeableIssuers() {
        if (sharesPrimaryIssuer() && !matchesPrimaryTenantAndKeys()) {
            throw new IllegalStateException(
                    "auth.cp-issuer equals auth.issuer but the tenant or JWKS URI differs.");
        }
    }

    private boolean isCpAudienceMissing() {
        return isCpIssuerEnabled() && cpAudience.isEmpty();
    }

    private boolean hasCpValuesWithoutTenant() {
        return !isCpIssuerEnabled() && (!cpAudience.isEmpty() || !cpIssuer.isEmpty() || !cpJwksUri.isEmpty());
    }

    /** CP configured in the same tenant as the primary issuer, so both produce the same {@code iss}. */
    private boolean sharesPrimaryIssuer() {
        return isCpIssuerEnabled() && cpIssuer.equalsIgnoreCase(issuer);
    }

    private boolean matchesPrimaryTenantAndKeys() {
        return cpTenantId.equals(tenantId) && cpJwksUri.equalsIgnoreCase(jwksUri);
    }

    /** Same tenant for both means one {@code iss}, so it becomes one issuer accepting both audiences. */
    private List<TrustedIssuer> buildTrustedIssuers() {
        final TrustedIssuer primary =
                new TrustedIssuer(PRIMARY_ISSUER_NAME, issuer, tenantId, Set.of(audience), jwksUri);
        final List<TrustedIssuer> issuers;
        if (sharesPrimaryIssuer()) {
            issuers = List.of(new TrustedIssuer(PRIMARY_ISSUER_NAME + "+" + CP_ISSUER_NAME,
                    issuer, tenantId, Set.copyOf(List.of(audience, cpAudience)), jwksUri));
        } else if (isCpIssuerEnabled()) {
            issuers = List.of(primary,
                    new TrustedIssuer(CP_ISSUER_NAME, cpIssuer, cpTenantId, Set.of(cpAudience), cpJwksUri));
        } else {
            issuers = List.of(primary);
        }
        return issuers;
    }

    /** An explicit value wins; otherwise derived from the tenant, or blank when no tenant is set. */
    private static String derivedOrBlank(final String explicit, final String tenant, final boolean issuer) {
        final String value;
        if (explicit.isBlank() && tenant.isEmpty()) {
            value = "";
        } else if (explicit.isBlank()) {
            value = issuer ? defaultIssuer(tenant) : defaultJwksUri(tenant);
        } else {
            value = explicit.trim();
        }
        return value;
    }

    /** Tenant ids are GUIDs: trimmed and lower-cased, as Entra emits {@code tid}. */
    private static String normaliseId(final String id) {
        return id.trim().toLowerCase(Locale.ROOT);
    }

    private static String defaultIssuer(final String tenantId) {
        return ENTRA_BASE_URL + tenantId + "/v2.0";
    }

    private static String defaultJwksUri(final String tenantId) {
        return ENTRA_BASE_URL + tenantId + "/discovery/v2.0/keys";
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
                                                       final Set<String> roles) {
        if (mode != AuthMode.OFF) {
            requireConfigured(tenantId, "auth.tenant-id", "AUTH_TENANT_ID",
                    "the tenant that issues the tokens, which is not necessarily the tenant hosting this service");
            requireConfigured(audience, "auth.audience", "AUTH_AUDIENCE",
                    "this API's own audience. A blank value must never be read as 'accept any audience'");
            requireConfigured(String.join(",", roles), "auth.roles", "AUTH_ROLES",
                    "a comma-separated list of the application roles this API recognises, exactly as the "
                            + "app registration declares them");
        }
    }

    private static Set<String> parseRoles(final String roles) {
        return Arrays.stream(roles.split(","))
                .map(String::trim)
                .filter(role -> !role.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
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
