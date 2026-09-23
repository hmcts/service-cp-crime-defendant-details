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

    private final MeterRegistry registry;
    private final Counter accepted;

    public AuthMetrics(final MeterRegistry registry) {
        this.registry = registry;
        this.accepted = Counter.builder("auth_tokens_accepted_total")
                .description("Access tokens that were validated successfully")
                .register(registry);
    }

    public void recordAccepted() {
        accepted.increment();
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
