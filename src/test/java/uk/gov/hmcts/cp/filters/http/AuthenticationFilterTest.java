package uk.gov.hmcts.cp.filters.http;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.ObjectMapper;
import uk.gov.hmcts.cp.auth.AuthMetrics;
import uk.gov.hmcts.cp.auth.AuthMode;
import uk.gov.hmcts.cp.auth.AuthProperties;
import uk.gov.hmcts.cp.auth.EntraTokenValidator;
import uk.gov.hmcts.cp.auth.TestTokens;
import uk.gov.hmcts.cp.auth.ValidatedCaller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Header and scheme handling, and what each mode actually does with a rejection.
 *
 * <p>The validator itself is not stubbed: these run the real one against the test key set, so the
 * filter and the validator are exercised together.
 */
class AuthenticationFilterTest {

    private static final String PROTECTED_PATH = "/defendants/cases/20GD1234567";

    private SimpleMeterRegistry meterRegistry;
    private FilterChain chain;

    @BeforeEach
    void beforeEach() {
        meterRegistry = new SimpleMeterRegistry();
        chain = mock(FilterChain.class);
    }

    @Test
    void a_valid_token_is_accepted_and_the_caller_is_put_on_the_request() throws Exception {
        final MockHttpServletRequest request = requestWith("Bearer " + TestTokens.validToken());
        final MockHttpServletResponse response = new MockHttpServletResponse();

        filter(AuthMode.ENFORCE).doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat((ValidatedCaller) request.getAttribute(AuthenticationFilter.CALLER_ATTRIBUTE))
                .isNotNull()
                .extracting(ValidatedCaller::clientId)
                .isEqualTo(TestTokens.CLIENT_ID);
        assertThat(counter("auth_tokens_accepted_total")).isEqualTo(1);
    }

    /** RFC 6750 section 2.1. A case-sensitive match would reject legitimate clients. */
    @ParameterizedTest
    @ValueSource(strings = {"Bearer", "bearer", "BEARER", "BeArEr"})
    void the_bearer_scheme_is_matched_case_insensitively(final String scheme) throws Exception {
        final MockHttpServletRequest request = requestWith(scheme + ' ' + TestTokens.validToken());
        final MockHttpServletResponse response = new MockHttpServletResponse();

        filter(AuthMode.ENFORCE).doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(chain).doFilter(request, response);
    }

    @Test
    void a_missing_authorization_header_is_rejected_without_an_error_code() throws Exception {
        final MockHttpServletResponse response = new MockHttpServletResponse();

        filter(AuthMode.ENFORCE).doFilter(requestWith(null), response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getHeader("WWW-Authenticate")).isEqualTo("Bearer");
        verify(chain, never()).doFilter(any(), any());
        assertThat(counter("auth_tokens_rejected_total")).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "Basic dXNlcjpwYXNz", "Bearer", "Bearer    ", "Token abc"})
    void a_blank_non_bearer_or_empty_credential_is_rejected(final String header) throws Exception {
        final MockHttpServletResponse response = new MockHttpServletResponse();

        filter(AuthMode.ENFORCE).doFilter(requestWith(header), response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getHeader("WWW-Authenticate")).startsWith("Bearer");
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void a_token_without_the_required_role_is_forbidden_rather_than_unauthorised() throws Exception {
        final String token = TestTokens.sign(TestTokens.validClaims()
                .claim("roles", java.util.List.of("Some.Other.Role"))
                .build());
        final MockHttpServletResponse response = new MockHttpServletResponse();

        filter(AuthMode.ENFORCE).doFilter(requestWith("Bearer " + token), response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getHeader("WWW-Authenticate")).contains("insufficient_scope");
        verify(chain, never()).doFilter(any(), any());
    }

    /** The error body must carry the coarse reason and nothing taken from the token. */
    @Test
    void the_error_body_never_contains_token_material() throws Exception {
        final String token = TestTokens.sign(TestTokens.validClaims()
                .audience(TestTokens.GRAPH_AUDIENCE)
                .build());
        final MockHttpServletResponse response = new MockHttpServletResponse();

        filter(AuthMode.ENFORCE).doFilter(requestWith("Bearer " + token), response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString())
                .doesNotContain(token, TestTokens.GRAPH_AUDIENCE, TestTokens.CLIENT_ID.toString())
                .contains("Access token claims are not valid");
    }

    /**
     * OBSERVE serves the request anyway. The counter it increments is deliberately separate from
     * the rejection counter, so a dashboard cannot make a non-enforcing environment look protected.
     */
    @Test
    void observe_mode_serves_the_request_and_counts_what_it_would_have_rejected() throws Exception {
        final MockHttpServletRequest request = requestWith(null);
        final MockHttpServletResponse response = new MockHttpServletResponse();

        filter(AuthMode.OBSERVE).doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(counter("auth_tokens_would_reject_total")).isEqualTo(1);
        assertThat(counter("auth_tokens_rejected_total")).isZero();
    }

    /** The unverified path validates nothing and leaves no caller behind to be mistaken for one. */
    @Test
    void off_mode_validates_nothing_and_flags_the_caller_unverified() throws Exception {
        final MockHttpServletRequest request = requestWith("Bearer not-even-a-token");
        final MockHttpServletResponse response = new MockHttpServletResponse();

        filter(AuthMode.OFF).doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(request.getAttribute(AuthenticationFilter.CALLER_ATTRIBUTE)).isNull();
        assertThat(counter("auth_tokens_accepted_total")).isZero();
        assertThat(counter("auth_tokens_would_reject_total")).isZero();
    }

    @Test
    void exempt_paths_skip_the_filter_entirely() {
        final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");

        assertThat(filter(AuthMode.ENFORCE).shouldNotFilter(request)).isTrue();
    }

    @Test
    void a_traversal_cannot_present_a_protected_path_as_an_exempt_one() {
        final MockHttpServletRequest request = new MockHttpServletRequest("GET", PROTECTED_PATH);
        request.setRequestURI("/actuator/health/../../defendants/cases/20GD1234567");

        assertThat(filter(AuthMode.ENFORCE).shouldNotFilter(request)).isFalse();
    }

    private AuthenticationFilter filter(final AuthMode mode) {
        final AuthProperties properties = TestTokens.properties(mode);
        return new AuthenticationFilter(
                properties,
                new EntraTokenValidator(properties, TestTokens.jwkSource()),
                new AuthMetrics(meterRegistry),
                new ObjectMapper(),
                Mockito.mock(Tracer.class));
    }

    private static MockHttpServletRequest requestWith(final String authorization) {
        final MockHttpServletRequest request = new MockHttpServletRequest("GET", PROTECTED_PATH);
        if (authorization != null) {
            request.addHeader("Authorization", authorization);
        }
        return request;
    }

    private double counter(final String name) {
        return meterRegistry.find(name).counters().stream()
                .mapToDouble(io.micrometer.core.instrument.Counter::count)
                .sum();
    }
}
