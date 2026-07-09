package uk.gov.hmcts.cp.filters;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import java.io.IOException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TracingFilterTest {

    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;
    @Mock
    private FilterChain filterChain;
    @Mock
    UUIDService uuidService;

    @InjectMocks
    TracingFilter tracingFilter;

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void filter_should_skip_actuators() {
        when(request.getRequestURI()).thenReturn("http://localhost/actuator/info");
        assertThat(tracingFilter.shouldNotFilter(request)).isTrue();

        when(request.getRequestURI()).thenReturn("http://localhost:8080/actuator/info");
        assertThat(tracingFilter.shouldNotFilter(request)).isTrue();

        when(request.getRequestURI()).thenReturn("https://example.com/actuator/info");
        assertThat(tracingFilter.shouldNotFilter(request)).isTrue();
    }

    @Test
    void filter_should_skip_root() {
        when(request.getRequestURI()).thenReturn("http://localhost/");
        assertThat(tracingFilter.shouldNotFilter(request)).isTrue();

        when(request.getRequestURI()).thenReturn("http://localhost:8080/");
        assertThat(tracingFilter.shouldNotFilter(request)).isTrue();

        when(request.getRequestURI()).thenReturn("https://example.com/");
        assertThat(tracingFilter.shouldNotFilter(request)).isTrue();
    }

    @Test
    void filter_should_not_skip_other_url_paths() {
        when(request.getRequestURI()).thenReturn("http://localhost/anything-else");
        assertThat(tracingFilter.shouldNotFilter(request)).isFalse();

        when(request.getRequestURI()).thenReturn("http://localhost:8080/anything-else");
        assertThat(tracingFilter.shouldNotFilter(request)).isFalse();

        when(request.getRequestURI()).thenReturn("https://example.com/a/long/url");
        assertThat(tracingFilter.shouldNotFilter(request)).isFalse();
    }

    @Test
    void doFilterInternal_puts_correlationId_in_MDC_and_response_when_header_present() throws ServletException, IOException {
        final String correlationId = UUID.randomUUID().toString();
        when(request.getHeader(TracingFilter.CORRELATION_ID_KEY)).thenReturn(correlationId);

        tracingFilter.doFilterInternal(request, response, filterChain);

        verify(response).setHeader(TracingFilter.CORRELATION_ID_KEY, correlationId);
        verify(filterChain).doFilter(request, response);
        assertThat(MDC.get(TracingFilter.CORRELATION_ID_KEY)).isNull();
    }

    @Test
    void doFilterInternal_generates_correlationId_when_header_absent() throws ServletException, IOException {
        String correlationId = "283bdad6-6a67-4e74-9f28-e556d7410e59";
        when(uuidService.randomString()).thenReturn(correlationId);
        when(request.getHeader(TracingFilter.CORRELATION_ID_KEY)).thenReturn(null);

        tracingFilter.doFilterInternal(request, response, filterChain);

        verify(response).setHeader(TracingFilter.CORRELATION_ID_KEY, correlationId);
        verify(filterChain).doFilter(request, response);
    }

}
