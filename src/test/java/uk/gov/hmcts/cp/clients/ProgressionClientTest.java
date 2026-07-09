package uk.gov.hmcts.cp.clients;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;
import uk.gov.hmcts.cp.config.AppPropertiesBackend;
import uk.gov.hmcts.cp.domain.ProgressionResponse;

import java.util.UUID;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProgressionClientTest {
    @Mock
    private AppPropertiesBackend appProperties;
    @Mock
    private RestClient restClient;
    @Mock
    private RestClient.RequestHeadersUriSpec requestHeadersUriSpec;
    @Mock
    private RestClient.ResponseSpec responseSpec;

    @InjectMocks
    private ProgressionClient progressionClient;

    @Test
    @SuppressWarnings("unchecked")
    void getProgressionResponseByCaseUrn_shouldReturnValidResponse() {
        ProgressionResponse progressionResponse = Mockito.mock(ProgressionResponse.class);
        when(appProperties.getProgressionCjscppuid()).thenReturn("CF2133");
        when(appProperties.getProgressionUrl()).thenReturn("http://localhost");
        when(appProperties.getProgressionPath()).thenReturn("/progression-query-api/query/api/rest/progression/prosecutioncases");
        UUID caseId = UUID.randomUUID();

        when(restClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(Mockito.anyString())).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.header(Mockito.anyString(), Mockito.anyString())).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(ProgressionResponse.class)).thenReturn(progressionResponse);

        ProgressionResponse response = progressionClient.getProgressionResponse(caseId);

        String url = "http://localhost/progression-query-api/query/api/rest/progression/prosecutioncases/".concat(caseId.toString());
        verify(requestHeadersUriSpec).uri(eq(url));
        verify(requestHeadersUriSpec).header("Accept", "application/vnd.progression.query.prosecutioncase+json");
        verify(requestHeadersUriSpec).header("CJSCPPUID", "CF2133");
        assertThat(progressionResponse).isEqualTo(response);
    }
}
