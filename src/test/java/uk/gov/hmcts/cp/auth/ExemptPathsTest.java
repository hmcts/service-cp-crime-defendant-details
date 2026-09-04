package uk.gov.hmcts.cp.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class ExemptPathsTest {

    @ParameterizedTest
    @ValueSource(strings = {
        "/",
        "/actuator/health",
        "/actuator/health/liveness",
        "/actuator/health/readiness",
        "/actuator/info",
        "/actuator/prometheus"})
    void every_declared_exempt_path_is_exempt(final String path) {
        assertThat(ExemptPaths.isExempt(path)).isTrue();
    }

    /**
     * The property a prefix rule cannot give you. Each of these is one character or one segment
     * away from an exempt path, and every one of them must still require a token.
     */
    @ParameterizedTest
    @ValueSource(strings = {
        "/actuator",
        "/actuator/",
        "/actuator/healthx",
        "/actuator/health/",
        "/actuator/health/custom",
        "/actuator/env",
        "/actuator/beans",
        "/actuator/heapdump",
        "/actuator/prometheusx",
        "/actuatorx/health",
        "/defendants/cases/20GD1234567",
        "/defendants",
        ""})
    void near_miss_paths_are_not_exempt(final String path) {
        assertThat(ExemptPaths.isExempt(path)).isFalse();
    }

    /**
     * Adding to the exempt set is a security change. This assertion makes one impossible to slip
     * in without a reviewer seeing it here.
     */
    @Test
    void the_exempt_set_has_not_silently_grown() {
        assertThat(ExemptPaths.all()).containsExactlyInAnyOrder(
                "/",
                "/actuator/health",
                "/actuator/health/liveness",
                "/actuator/health/readiness",
                "/actuator/info",
                "/actuator/prometheus");
    }
}
