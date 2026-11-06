package uk.gov.hmcts.cp.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;

import java.util.Locale;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The service must fail to start rather than degrade into one that accepts anything. Each test
 * here corresponds to a way that could otherwise happen quietly.
 */
class AuthPropertiesTest {

    private static final String TENANT = "11111111-1111-1111-1111-111111111111";
    private static final String AUDIENCE = "22222222-2222-2222-2222-222222222222";
    private static final String ROLE = "DefendantDetails.Read";
    private static final String CP_TENANT = "66666666-6666-6666-6666-666666666666";
    private static final String CP_AUDIENCE = "77777777-7777-7777-7777-777777777777";

    /**
     * The audience check is the only thing that rejects a genuine, correctly signed, unexpired
     * token minted for a different resource. A blank one must never be read as "accept any".
     */
    @Test
    void startup_fails_when_the_audience_is_blank_and_the_mode_is_enforcing() {
        assertThatThrownBy(() -> properties(AuthMode.ENFORCE, deployed(), TENANT, "  ", ROLE, 60))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("auth.audience")
                .hasMessageContaining("AUTH_AUDIENCE");
    }

    @Test
    void startup_fails_when_the_tenant_is_blank_and_the_mode_is_enforcing() {
        assertThatThrownBy(() -> properties(AuthMode.ENFORCE, deployed(), "", AUDIENCE, ROLE, 60))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("auth.tenant-id");
    }

    /**
     * There is no default role: a missing AUTH_ROLES must stop the pod starting, not surface later
     * as every caller getting a 403.
     */
    @ParameterizedTest
    @ValueSource(strings = {"", " ", ",", " , ,"})
    void startup_fails_when_the_roles_allowlist_is_blank_and_the_mode_is_enforcing(final String roles) {
        assertThatThrownBy(() -> properties(AuthMode.ENFORCE, deployed(), TENANT, AUDIENCE, roles, 60))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("auth.roles")
                .hasMessageContaining("AUTH_ROLES");
    }

    @Test
    void the_roles_allowlist_is_split_on_commas_and_trimmed() {
        final AuthProperties properties = properties(AuthMode.ENFORCE, deployed(), TENANT, AUDIENCE,
                " DefendantDetails.Read ,,DefendantDetails.ReadAll ", 60);

        assertThat(properties.getRoles())
                .containsExactlyInAnyOrder("DefendantDetails.Read", "DefendantDetails.ReadAll");
    }

    /**
     * OFF and OBSERVE provide no protection. A deployed pod carries neither the local nor the test
     * profile, so it cannot be put into one by an environment variable alone.
     */
    @ParameterizedTest
    @EnumSource(value = AuthMode.class, names = {"OFF", "OBSERVE"})
    void startup_fails_for_a_non_enforcing_mode_in_a_deployed_environment(final AuthMode mode) {
        assertThatThrownBy(() -> properties(mode, deployed(), TENANT, AUDIENCE, ROLE, 60))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("provides no protection")
                .hasMessageContaining("must run in ENFORCE");
    }

    @ParameterizedTest
    @ValueSource(strings = {"local", "test"})
    void a_non_enforcing_mode_is_permitted_only_where_the_local_or_test_profile_is_active(final String profile) {
        final MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profile);

        assertThatCode(() -> properties(AuthMode.OFF, environment, "", "", ROLE, 60))
                .doesNotThrowAnyException();
    }

    /** A large skew turns exp into a no-op. */
    @Test
    void startup_fails_when_the_clock_skew_exceeds_the_cap() {
        assertThatThrownBy(() -> properties(AuthMode.ENFORCE, deployed(), TENANT, AUDIENCE, ROLE,
                AuthProperties.MAX_CLOCK_SKEW_SECONDS + 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("turns the exp claim into a no-op");
    }

    @Test
    void startup_fails_when_the_clock_skew_is_negative() {
        assertThatThrownBy(() -> properties(AuthMode.ENFORCE, deployed(), TENANT, AUDIENCE, ROLE, -1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("auth.clock-skew-seconds");
    }

    @Test
    void the_issuer_and_jwks_uri_are_derived_from_the_issuing_tenant_when_not_overridden() {
        final AuthProperties properties = properties(AuthMode.ENFORCE, deployed(), TENANT, AUDIENCE, ROLE, 60);

        assertThat(properties.getIssuer())
                .isEqualTo("https://login.microsoftonline.com/" + TENANT + "/v2.0");
        assertThat(properties.getJwksUri())
                .isEqualTo("https://login.microsoftonline.com/" + TENANT + "/discovery/v2.0/keys");
    }

    @Test
    void an_explicit_issuer_and_jwks_uri_override_the_derived_ones() {
        final AuthProperties properties = new AuthProperties(deployed(), AuthMode.ENFORCE, TENANT, AUDIENCE,
                "https://issuer.test/v2.0", "https://issuer.test/keys", "", "", "", "", ROLE, 60, 300);

        assertThat(properties.getIssuer()).isEqualTo("https://issuer.test/v2.0");
        assertThat(properties.getJwksUri()).isEqualTo("https://issuer.test/keys");
    }

    @Test
    void only_enforce_actually_rejects_a_request() {
        assertThat(properties(AuthMode.ENFORCE, deployed(), TENANT, AUDIENCE, ROLE, 60).isEnforcing()).isTrue();
        assertThat(properties(AuthMode.OBSERVE, local(), TENANT, AUDIENCE, ROLE, 60).isEnforcing()).isFalse();
        assertThat(properties(AuthMode.OBSERVE, local(), TENANT, AUDIENCE, ROLE, 60).isValidating()).isTrue();
        assertThat(properties(AuthMode.OFF, local(), "", "", ROLE, 60).isValidating()).isFalse();
    }

    @Test
    void with_no_cp_values_only_the_primary_issuer_is_trusted() {
        final AuthProperties properties = properties(AuthMode.ENFORCE, deployed(), TENANT, AUDIENCE, ROLE, 60);

        assertThat(properties.isCpIssuerEnabled()).isFalse();
        assertThat(properties.getTrustedIssuers()).extracting(TrustedIssuer::name).containsExactly("primary");
    }

    @Test
    void a_cp_tenant_and_audience_add_a_second_separate_trusted_issuer() {
        final AuthProperties properties = withCp(AuthMode.ENFORCE, CP_TENANT, CP_AUDIENCE, "", "");

        assertThat(properties.getTrustedIssuers()).containsExactly(
                new TrustedIssuer("primary", issuerFor(TENANT), TENANT, Set.of(AUDIENCE), jwksUriFor(TENANT)),
                new TrustedIssuer("cp", issuerFor(CP_TENANT), CP_TENANT, Set.of(CP_AUDIENCE), jwksUriFor(CP_TENANT)));
    }

    @Test
    void startup_fails_when_a_cp_tenant_has_no_cp_audience() {
        assertThatThrownBy(() -> withCp(AuthMode.ENFORCE, CP_TENANT, " ", "", ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("auth.cp-audience")
                .hasMessageContaining("AUTH_CP_AUDIENCE");
    }

    @ParameterizedTest
    @ValueSource(strings = {"audience", "issuer", "jwks"})
    void startup_fails_when_any_other_cp_value_is_set_without_a_cp_tenant(final String which) {
        assertThatThrownBy(() -> withCp(AuthMode.ENFORCE, "",
                "audience".equals(which) ? CP_AUDIENCE : "",
                "issuer".equals(which) ? issuerFor(CP_TENANT) : "",
                "jwks".equals(which) ? jwksUriFor(CP_TENANT) : ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("auth.cp-tenant-id")
                .hasMessageContaining("AUTH_CP_TENANT_ID");
    }

    @Test
    void a_half_set_cp_pair_fails_startup_even_when_off() {
        assertThatThrownBy(() -> new AuthProperties(local(), AuthMode.OFF, "", "", "", "",
                CP_TENANT, "", "", "", ROLE, 60, 300))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("auth.cp-audience");
    }

    @ParameterizedTest
    @ValueSource(strings = {TENANT, "  11111111-1111-1111-1111-111111111111 "})
    void cp_in_the_same_tenant_becomes_one_issuer_accepting_both_audiences(final String cpTenant) {
        final AuthProperties properties = withCp(AuthMode.ENFORCE, cpTenant.toUpperCase(Locale.ROOT), CP_AUDIENCE, "", "");

        assertThat(properties.getTrustedIssuers()).singleElement().satisfies(trusted -> {
            assertThat(trusted.name()).isEqualTo("primary+cp");
            assertThat(trusted.tenantId()).isEqualTo(TENANT);
            assertThat(trusted.audiences()).containsExactlyInAnyOrder(AUDIENCE, CP_AUDIENCE);
        });
    }

    @Test
    void the_same_tenant_and_audience_for_both_is_one_issuer_with_one_audience() {
        final AuthProperties properties = withCp(AuthMode.ENFORCE, TENANT, AUDIENCE, "", "");

        assertThat(properties.getTrustedIssuers()).singleElement()
                .satisfies(trusted -> assertThat(trusted.audiences()).containsExactly(AUDIENCE));
    }

    @Test
    void startup_fails_when_the_cp_issuer_equals_the_primary_issuer_in_another_tenant() {
        assertThatThrownBy(() -> withCp(AuthMode.ENFORCE, CP_TENANT, CP_AUDIENCE, issuerFor(TENANT), ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("auth.cp-issuer equals auth.issuer");
    }

    @Test
    void startup_fails_when_the_cp_issuer_equals_the_primary_issuer_with_other_keys() {
        assertThatThrownBy(() -> withCp(AuthMode.ENFORCE, TENANT, CP_AUDIENCE, "", "https://issuer.test/keys"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("auth.cp-issuer equals auth.issuer");
    }

    @Test
    void tenant_ids_are_trimmed_and_lower_cased_as_entra_emits_tid() {
        final AuthProperties properties = new AuthProperties(deployed(), AuthMode.ENFORCE,
                " " + TENANT.toUpperCase(Locale.ROOT) + " ", AUDIENCE, "", "",
                "  " + CP_TENANT.toUpperCase(Locale.ROOT) + " ", " " + CP_AUDIENCE + " ", "", "", ROLE, 60, 300);

        assertThat(properties.getTenantId()).isEqualTo(TENANT);
        assertThat(properties.getIssuer()).isEqualTo(issuerFor(TENANT));
        assertThat(properties.getCpTenantId()).isEqualTo(CP_TENANT);
        assertThat(properties.getCpAudience()).isEqualTo(CP_AUDIENCE);
        assertThat(properties.getCpIssuer()).isEqualTo(issuerFor(CP_TENANT));
        assertThat(properties.getCpJwksUri()).isEqualTo(jwksUriFor(CP_TENANT));
    }

    private static AuthProperties withCp(final AuthMode mode,
                                         final String cpTenant,
                                         final String cpAudience,
                                         final String cpIssuer,
                                         final String cpJwksUri) {
        return new AuthProperties(deployed(), mode, TENANT, AUDIENCE, "", "",
                cpTenant, cpAudience, cpIssuer, cpJwksUri, ROLE, 60, 300);
    }

    private static String issuerFor(final String tenant) {
        return "https://login.microsoftonline.com/" + tenant + "/v2.0";
    }

    private static String jwksUriFor(final String tenant) {
        return "https://login.microsoftonline.com/" + tenant + "/discovery/v2.0/keys";
    }

    private static AuthProperties properties(final AuthMode mode,
                                             final Environment environment,
                                             final String tenantId,
                                             final String audience,
                                             final String roles,
                                             final long clockSkewSeconds) {
        return new AuthProperties(environment, mode, tenantId, audience, "", "", "", "", "", "",
                roles, clockSkewSeconds, 300);
    }

    /** A deployed pod: no local or test profile. */
    private static Environment deployed() {
        final MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("sit");
        return environment;
    }

    private static Environment local() {
        final MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("local");
        return environment;
    }
}
