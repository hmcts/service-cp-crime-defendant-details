package uk.gov.hmcts.cp.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;

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

    @Test
    void startup_fails_when_the_required_role_is_blank_and_the_mode_is_enforcing() {
        assertThatThrownBy(() -> properties(AuthMode.ENFORCE, deployed(), TENANT, AUDIENCE, " ", 60))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("auth.required-role");
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
                "https://issuer.test/v2.0", "https://issuer.test/keys", ROLE, 60, 300);

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

    private static AuthProperties properties(final AuthMode mode,
                                             final Environment environment,
                                             final String tenantId,
                                             final String audience,
                                             final String requiredRole,
                                             final long clockSkewSeconds) {
        return new AuthProperties(environment, mode, tenantId, audience, "", "",
                requiredRole, clockSkewSeconds, 300);
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
