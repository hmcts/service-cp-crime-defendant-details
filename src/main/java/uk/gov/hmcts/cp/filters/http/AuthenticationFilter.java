package uk.gov.hmcts.cp.filters.http;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import jakarta.annotation.Nonnull;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import uk.gov.hmcts.cp.auth.AuthMetrics;
import uk.gov.hmcts.cp.auth.AuthProperties;
import uk.gov.hmcts.cp.auth.EntraTokenValidator;
import uk.gov.hmcts.cp.auth.ExemptPaths;
import uk.gov.hmcts.cp.auth.TokenRejectionReason;
import uk.gov.hmcts.cp.auth.TokenValidationException;
import uk.gov.hmcts.cp.auth.ValidatedCaller;
import uk.gov.hmcts.cp.openapi.model.ErrorResponse;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.Locale;

import tools.jackson.databind.ObjectMapper;

/**
 * Rejects requests that do not carry a valid Entra app-only access token.
 *
 * <p>Ordered immediately after {@link TracingFilter} so that a rejection is still logged with a
 * correlation id. This ordering is the reason the service verifies tokens with Nimbus directly
 * rather than adding a security framework, whose filter chain registers at its own order.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@Slf4j
public class AuthenticationFilter extends OncePerRequestFilter {

    /** Request attribute holding the {@link ValidatedCaller} once a token has been validated. */
    public static final String CALLER_ATTRIBUTE = "uk.gov.hmcts.cp.auth.caller";

    /** MDC key for the calling application's client id, so every log line can be attributed. */
    public static final String CLIENT_ID_KEY = "clientId";

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "bearer ";
    private static final String UNVERIFIED_CLIENT_ID = "unverified";

    private final AuthProperties authProperties;
    private final EntraTokenValidator tokenValidator;
    private final AuthMetrics authMetrics;
    private final ObjectMapper objectMapper;
    private final Tracer tracer;

    public AuthenticationFilter(final AuthProperties authProperties,
                                final EntraTokenValidator tokenValidator,
                                final AuthMetrics authMetrics,
                                final ObjectMapper objectMapper,
                                final Tracer tracer) {
        this.authProperties = authProperties;
        this.tokenValidator = tokenValidator;
        this.authMetrics = authMetrics;
        this.objectMapper = objectMapper;
        this.tracer = tracer;
    }

    @Override
    protected boolean shouldNotFilter(@Nonnull final HttpServletRequest request) {
        return ExemptPaths.isExempt(normalisedPath(request));
    }

    @Override
    protected void doFilterInternal(@Nonnull final HttpServletRequest request,
                                    @Nonnull final HttpServletResponse response,
                                    @Nonnull final FilterChain filterChain)
            throws ServletException, IOException {

        if (!authProperties.isValidating()) {
            proceedUnverified(request, response, filterChain);
            return;
        }

        try {
            final ValidatedCaller caller = tokenValidator.validate(bearerToken(request));
            authMetrics.recordAccepted();
            proceedAsCaller(request, response, filterChain, caller);
        } catch (TokenValidationException e) {
            handleRejection(request, response, filterChain, e.getReason());
        }
    }

    /**
     * In OBSERVE the failure is counted and logged but the request is served anyway, with the
     * caller left explicitly unverified so that downstream code cannot mistake it for an
     * authenticated one.
     */
    private void handleRejection(final HttpServletRequest request,
                                 final HttpServletResponse response,
                                 final FilterChain filterChain,
                                 final TokenRejectionReason reason) throws ServletException, IOException {
        if (authProperties.isEnforcing()) {
            authMetrics.recordRejected(reason);
            log.warn("Rejecting {} {}: {}", request.getMethod(), normalisedPath(request), reason);
            writeChallenge(response, reason);
        } else {
            authMetrics.recordWouldReject(reason);
            log.warn("Access token would have been rejected for {} {}: {}. auth.mode is {}, so the "
                            + "request is being served anyway and this endpoint is not protected.",
                    request.getMethod(), normalisedPath(request), reason, authProperties.getMode());
            proceedUnverified(request, response, filterChain);
        }
    }

    private void proceedAsCaller(final HttpServletRequest request,
                                 final HttpServletResponse response,
                                 final FilterChain filterChain,
                                 final ValidatedCaller caller) throws ServletException, IOException {
        request.setAttribute(CALLER_ATTRIBUTE, caller);
        MDC.put(CLIENT_ID_KEY, caller.clientId().toString());
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(CLIENT_ID_KEY);
        }
    }

    private void proceedUnverified(final HttpServletRequest request,
                                   final HttpServletResponse response,
                                   final FilterChain filterChain) throws ServletException, IOException {
        MDC.put(CLIENT_ID_KEY, UNVERIFIED_CLIENT_ID);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(CLIENT_ID_KEY);
        }
    }

    /**
     * Extracts the bare token, matching the Bearer scheme case-insensitively as RFC 6750 section
     * 2.1 requires - a case-sensitive match rejects legitimate clients.
     */
    private static String bearerToken(final HttpServletRequest request) throws TokenValidationException {
        final String header = request.getHeader(AUTHORIZATION_HEADER);
        if (header == null) {
            throw new TokenValidationException(TokenRejectionReason.MISSING_AUTHORIZATION_HEADER);
        }
        final String trimmed = header.trim();
        if (trimmed.isEmpty()) {
            throw new TokenValidationException(TokenRejectionReason.BLANK_AUTHORIZATION_HEADER);
        }
        if (!trimmed.toLowerCase(Locale.ROOT).startsWith(BEARER_PREFIX)) {
            throw new TokenValidationException(TokenRejectionReason.UNSUPPORTED_AUTHORIZATION_SCHEME);
        }
        final String token = trimmed.substring(BEARER_PREFIX.length()).trim();
        if (token.isEmpty()) {
            throw new TokenValidationException(TokenRejectionReason.EMPTY_BEARER_TOKEN);
        }
        return token;
    }

    /**
     * Writes the RFC 6750 challenge and an error body in the shape the API contract declares.
     *
     * <p>The response is built here rather than by the {@code @RestControllerAdvice}, because this
     * filter runs before the DispatcherServlet and an exception thrown from it would never reach
     * the advice.
     */
    private void writeChallenge(final HttpServletResponse response,
                                final TokenRejectionReason reason) throws IOException {
        response.setStatus(reason.getStatus().value());
        response.setHeader("WWW-Authenticate", challenge(reason));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), ErrorResponse.builder()
                .error(reason.getStatus().name())
                .message(reason.getMessage())
                .timestamp(Instant.now())
                .traceId(traceId())
                .build());
    }

    private static String challenge(final TokenRejectionReason reason) {
        final String challenge;
        if (reason.getErrorCode() == null) {
            challenge = "Bearer";
        } else {
            challenge = String.format("Bearer error=\"%s\", error_description=\"%s\"",
                    reason.getErrorCode(), reason.getMessage());
        }
        return challenge;
    }

    /**
     * The current span if tracing has started one, otherwise the correlation id set by
     * {@link TracingFilter}, which always runs first.
     */
    private String traceId() {
        final Span span = tracer.currentSpan();
        return span == null ? MDC.get(TracingFilter.CORRELATION_ID_KEY) : span.context().traceId();
    }

    /**
     * Normalises the request path before matching it against the exempt set, so that a traversal
     * such as {@code /defendants/../actuator/health} cannot be presented as an exempt path.
     */
    private static String normalisedPath(final HttpServletRequest request) {
        return URI.create(request.getRequestURI()).normalize().getPath();
    }
}
