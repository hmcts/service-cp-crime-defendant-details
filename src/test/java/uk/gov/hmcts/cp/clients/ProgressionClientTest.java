package uk.gov.hmcts.cp.clients;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import uk.gov.hmcts.cp.config.AppPropertiesBackend;
import uk.gov.hmcts.cp.domain.ProgressionResponse;

import java.util.UUID;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProgressionClientTest {
    @Mock
    private AppPropertiesBackend appProperties;
    @Mock
    private RestTemplate restTemplate;

    @Captor
    private ArgumentCaptor<HttpEntity<String>> entityCaptor;

    @InjectMocks
    private ProgressionClient progressionClient;

    @Test
    void getProgressionResponseByCaseUrn_shouldReturnValidResponse() {
        ProgressionResponse progressionResponse = Mockito.mock(ProgressionResponse.class);
        when(appProperties.getProgressionCjscppuid()).thenReturn("CF2133");
        when(appProperties.getProgressionUrl()).thenReturn("http://localhost");
        when(appProperties.getProgressionPath()).thenReturn("/progression-query-api/query/api/rest/progression/prosecutioncases");
        UUID caseId = UUID.randomUUID();
        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(ProgressionResponse.class)
        )).thenReturn(ResponseEntity.ok(progressionResponse));

        ProgressionResponse response = progressionClient.getProgressionResponse(caseId);

        String url = "http://localhost/progression-query-api/query/api/rest/progression/prosecutioncases/".concat(caseId.toString());
        verify(restTemplate).exchange(eq(url),
                eq(HttpMethod.GET),
                entityCaptor.capture(),
                eq(ProgressionResponse.class));

        HttpEntity<?> sent = entityCaptor.getValue();
        assertThat(progressionResponse).isEqualTo(response);
        assertThat(sent.getHeaders()).isEqualTo(expectedHeaders().getHeaders());
    }

    private HttpEntity<String> expectedHeaders() {
        final HttpHeaders headers = new HttpHeaders();
        headers.set("Accept", "application/vnd.progression.query.prosecutioncase+json");
        headers.set("CJSCPPUID", appProperties.getProgressionCjscppuid());
        return new HttpEntity<>(headers);
    }
}