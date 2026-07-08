package uk.gov.hmcts.cp.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.cp.clients.CaseUrnMapperClient;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CaseUrnMapperServiceTest {
    @Mock
    private CaseUrnMapperClient caseUrnMapperClient;

    @InjectMocks
    private CaseUrnMapperService caseUrnMapperService;

    private final UUID caseId = UUID.fromString("6c7fd04c-0dae-4c96-aaff-bc60f4e0d431");
    private final String caseUrn = "test-case-urn";


    @Test
    void shouldReturnCaseIdWhenResponseIsSuccessful() {
        when(caseUrnMapperClient.getCaseId(caseUrn)).thenReturn(caseId);
        UUID responseCaseId = caseUrnMapperService.getCaseId(caseUrn);

        assertEquals(caseId, responseCaseId);
        verify(caseUrnMapperClient).getCaseId(caseUrn);
    }
}