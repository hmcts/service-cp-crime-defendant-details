package uk.gov.hmcts.cp.clients;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import uk.gov.hmcts.cp.config.AppPropertiesBackend;
import uk.gov.hmcts.cp.domain.CaseMapperResponse;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CaseUrnMapperClientTest {
    @Mock
    private RestTemplate restTemplate;
    @Mock
    private AppPropertiesBackend appProperties;

    @InjectMocks
    private CaseUrnMapperClient caseUrnMapperClient;

    private final String mockUrl = "http://mock-server/mapper";
    private final String mockPath = "/urnmapper";
    private final String caseUrn = "test-case-urn";
    private final UUID caseId = UUID.fromString("7a2e94c4-38af-43dd-906b-40d632d159b0");

    @Test
    void shouldReturnCaseIdWhenResponseIsSuccessful() {
        when(appProperties.getCaseMapperUrl()).thenReturn(mockUrl);
        when(appProperties.getCaseMapperPath()).thenReturn(mockPath);
        CaseMapperResponse response = CaseMapperResponse.builder()
                .caseId(caseId)
                .build();
        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(CaseMapperResponse.class)
        )).thenReturn(ResponseEntity.ok(response));

        UUID result = caseUrnMapperClient.getCaseId(caseUrn);

        assertEquals(caseId, result);
    }
}