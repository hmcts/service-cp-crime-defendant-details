package uk.gov.hmcts.cp.config;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;

import static uk.gov.hmcts.cp.filters.http.TracingFilter.CORRELATION_ID_KEY;

/**
 * Propagates the correlation ID from MDC onto every outbound REST call.
 * Register this as an interceptor on your RestClient bean.
 */
@Component
@Slf4j
public class OutboundTracingInterceptor implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(final HttpRequest request,
                                        final byte[] body,
                                        final ClientHttpRequestExecution execution) throws IOException {
        final String correlationId = MDC.get(CORRELATION_ID_KEY);
        request.getHeaders().set(CORRELATION_ID_KEY, correlationId);
        log.info("Outbound {} correlationId:{}", request.getURI(), correlationId);
        return execution.execute(request, body);
    }
}
