package uk.gov.hmcts.cp.auth;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Counters for access token validation outcomes.
 */
@Component
public class AuthMetrics {

    private static final String REASON_TAG = "reason";
    private static final String ISSUER_TAG = "issuer";

    private final MeterRegistry registry;

    public AuthMetrics(final MeterRegistry registry) {
        this.registry = registry;
    }

    /** Tagged with the {@link TrustedIssuer} name: {@code primary}, {@code cp} or {@code primary+cp}. */
    public void recordAccepted(final String issuerName) {
        Counter.builder("auth_tokens_accepted_total")
                .description("Access tokens that were validated successfully")
                .tag(ISSUER_TAG, issuerName)
                .register(registry)
                .increment();
    }

    public void recordRejected(final TokenRejectionReason reason) {
        registry.counter("auth_tokens_rejected_total", REASON_TAG, tag(reason)).increment();
    }

    /** What OBSERVE mode would have rejected, had it been enforcing. */
    public void recordWouldReject(final TokenRejectionReason reason) {
        registry.counter("auth_tokens_would_reject_total", REASON_TAG, tag(reason)).increment();
    }

    private static String tag(final TokenRejectionReason reason) {
        return reason.name().toLowerCase(Locale.UK);
    }
}
