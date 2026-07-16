package uk.gov.hmcts.cp.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.hmcts.cp.clients.ProgressionClient;
import uk.gov.hmcts.cp.domain.ProgressionResponse;
import uk.gov.hmcts.cp.domain.ProgressionResponse.ProsecutionCase;
import uk.gov.hmcts.cp.domain.ProgressionResponse.ProsecutionCase.Defendant;
import uk.gov.hmcts.cp.filters.service.DefendantFilter;
import uk.gov.hmcts.cp.mappers.DefendantDetailsMapper;
import uk.gov.hmcts.cp.openapi.model.DefendantDetails;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefendantDetailsServiceTest {

    @Mock
    private CaseUrnMapperService caseUrnMapperService;
    @Mock
    private ProgressionClient progressionClient;
    @Mock
    private DefendantDetailsMapper defendantDetailsMapper;
    @Mock
    private DefendantFilter defendantFilter;

    @InjectMocks
    private DefendantDetailsService defendantDetailsService;

    private final String caseUrn = "20GD1234567";
    private final UUID caseId = UUID.randomUUID();

    @Test
    void should_delegate_to_defendantFilter_and_map_its_result() {
        UUID masterDefendantId = UUID.randomUUID();
        UUID defendantId = UUID.randomUUID();
        UUID defendantId1 = UUID.randomUUID();
        UUID defendantId2 = UUID.randomUUID();
        Defendant defendant1 = Defendant.builder().id(defendantId1).build();
        Defendant defendant2 = Defendant.builder().id(defendantId2).build();
        List<Defendant> defendants = List.of(defendant1, defendant2);
        stubCase(defendants);
        when(defendantFilter.filter(defendants, masterDefendantId, defendantId)).thenReturn(defendants);

        DefendantDetails mapped1 = DefendantDetails.builder().defendantId(defendantId1).build();
        DefendantDetails mapped2 = DefendantDetails.builder().defendantId(defendantId2).build();
        when(defendantDetailsMapper.mapToDefendantDetails(defendant1)).thenReturn(mapped1);
        when(defendantDetailsMapper.mapToDefendantDetails(defendant2)).thenReturn(mapped2);

        List<DefendantDetails> result = defendantDetailsService.getDefendantsByCase(caseUrn, masterDefendantId, defendantId);

        assertThat(result).containsExactly(mapped1, mapped2);
        verify(defendantFilter).filter(defendants, masterDefendantId, defendantId);
    }

    @Test
    void should_return_empty_list_when_defendantFilter_returns_no_matches() {
        stubCase(List.of());
        when(defendantFilter.filter(List.of(), null, null)).thenReturn(List.of());

        List<DefendantDetails> result = defendantDetailsService.getDefendantsByCase(caseUrn, null, null);

        assertThat(result).isEmpty();
    }

    @Test
    void should_throw_404_when_no_prosecutionCase_found() {
        when(caseUrnMapperService.getCaseId(caseUrn)).thenReturn(caseId);
        when(progressionClient.getProgressionResponse(caseId)).thenReturn(ProgressionResponse.builder().build());

        assertThatThrownBy(() -> defendantDetailsService.getDefendantsByCase(caseUrn, null, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.NOT_FOUND);
    }

    @Test
    void should_throw_404_when_progression_response_is_null() {
        when(caseUrnMapperService.getCaseId(caseUrn)).thenReturn(caseId);
        when(progressionClient.getProgressionResponse(caseId)).thenReturn(null);

        assertThatThrownBy(() -> defendantDetailsService.getDefendantsByCase(caseUrn, null, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.NOT_FOUND);
    }

    private void stubCase(final List<Defendant> defendants) {
        when(caseUrnMapperService.getCaseId(caseUrn)).thenReturn(caseId);
        when(progressionClient.getProgressionResponse(caseId)).thenReturn(ProgressionResponse.builder()
                .prosecutionCase(ProsecutionCase.builder()
                        .defendants(defendants)
                        .build())
                .build());
    }
}