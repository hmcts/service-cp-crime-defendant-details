package uk.gov.hmcts.cp.clients;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;
import uk.gov.hmcts.cp.config.AppPropertiesBackend;
import uk.gov.hmcts.cp.openapi.model.CaseMapperResponse;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CaseUrnMapperClientTest {
    @Mock
    private RestClient restClient;
    @Mock
    private RestClient.RequestHeadersUriSpec requestHeadersUriSpec;
    @Mock
    private RestClient.ResponseSpec responseSpec;
    @Mock
    private AppPropertiesBackend appProperties;

    @InjectMocks
    private CaseUrnMapperClient caseUrnMapperClient;

    private final String mockUrl = "http://mock-server/mapper";
    private final String mockPath = "/urnmapper";
    private final String caseUrn = "test-case-urn";
    private final UUID caseId = UUID.fromString("7a2e94c4-38af-43dd-906b-40d632d159b0");

    @Test
    @SuppressWarnings("unchecked")
    void shouldReturnCaseMappingWhenResponseIsSuccessful() {
        when(appProperties.getCaseMapperUrl()).thenReturn(mockUrl);
        when(appProperties.getCaseMapperPath()).thenReturn(mockPath);

        final CaseMapperResponse response = CaseMapperResponse.builder()
                .caseId(caseId)
                .build();

        when(restClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(CaseMapperResponse.class)).thenReturn(response);

        final CaseMapperResponse result = caseUrnMapperClient.getCaseMapping(caseUrn);

        assertEquals(caseId, result.getCaseId());
    }
}