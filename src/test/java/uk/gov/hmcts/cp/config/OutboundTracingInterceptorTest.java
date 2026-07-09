package uk.gov.hmcts.cp.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static uk.gov.hmcts.cp.filters.TracingFilter.CORRELATION_ID_KEY;

@ExtendWith(MockitoExtension.class)
class OutboundTracingInterceptorTest {

    @InjectMocks OutboundTracingInterceptor interceptor;
    @Mock HttpRequest request;
    @Mock ClientHttpRequestExecution execution;
    @Mock ClientHttpResponse clientHttpResponse;

    @BeforeEach
    void setUp() { MDC.clear(); }

    @AfterEach
    void tearDown() { MDC.clear(); }

    @Test
    void outbound_request_should_carry_correlation_id_from_mdc() throws Exception {
        final String correlationId = UUID.randomUUID().toString();
        MDC.put(CORRELATION_ID_KEY, correlationId);
        final HttpHeaders headers = new HttpHeaders();
        when(request.getHeaders()).thenReturn(headers);
        when(execution.execute(request, new byte[0])).thenReturn(clientHttpResponse);

        interceptor.intercept(request, new byte[0], execution);

        assertThat(headers.getFirst(CORRELATION_ID_KEY)).isEqualTo(correlationId);
    }

    @Test
    void outbound_request_without_mdc_should_send_null_correlation_id() throws Exception {
        final HttpHeaders headers = new HttpHeaders();
        when(request.getHeaders()).thenReturn(headers);
        when(execution.execute(request, new byte[0])).thenReturn(clientHttpResponse);

        interceptor.intercept(request, new byte[0], execution);

        assertThat(headers.getFirst(CORRELATION_ID_KEY)).isNull();
    }
}
