package uk.gov.hmcts.cp.auth;

import java.util.Set;

/**
 * The endpoints that may be reached without a validated access token.
 *
 * <p>Enumerated and matched exactly, never by prefix.
 *
 * <p>Every entry is an infrastructure endpoint carrying no case data. Adding to this set is a
 * security change and must be reviewed as one.
 */
public final class ExemptPaths {

    private static final Set<String> EXEMPT = Set.of(
            // Root. Serves nothing; probed by load balancers.
            "/",
            // Kubernetes liveness and readiness probes, which send no Authorization header.
            "/actuator/health",
            "/actuator/health/liveness",
            "/actuator/health/readiness",
            // Build and git metadata only.
            "/actuator/info",
            // Scraped in-cluster by Prometheus, which sends no Authorization header.
            "/actuator/prometheus");

    private ExemptPaths() {
    }

    public static boolean isExempt(final String path) {
        return EXEMPT.contains(path);
    }

    /** The exempt set, so a test can assert it has not silently grown. */
    public static Set<String> all() {
        return EXEMPT;
    }
}
