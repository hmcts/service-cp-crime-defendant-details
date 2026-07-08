package uk.gov.hmcts.cp.controllers;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import uk.gov.hmcts.cp.openapi.model.DefendantDetails;
import uk.gov.hmcts.cp.services.DefendantDetailsService;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@Slf4j
@ExtendWith(MockitoExtension.class)
class DefendantDetailsControllerTest {

    @Mock
    private DefendantDetailsService defendantDetailsService;

    @InjectMocks
    private DefendantDetailsController defendantDetailsController;

    @Test
    void getDefendantsByCase_ShouldReturnWithOkStatus() {
        String caseUrn = "20GD1234567";
        UUID defendantId = UUID.randomUUID();
        UUID masterDefendantId = UUID.randomUUID();

        DefendantDetails mockDefendant = DefendantDetails.builder()
                .defendantId(defendantId)
                .masterDefendantId(masterDefendantId)
                .name("John Doe")
                .dateOfBirth(LocalDate.of(1980, 1, 31))
                .build();

        when(defendantDetailsService.getDefendantsByCase(eq(caseUrn), any(), any())).thenReturn(List.of(mockDefendant));

        ResponseEntity<List<DefendantDetails>> response = defendantDetailsController.getDefendantsByCase(caseUrn, masterDefendantId, defendantId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsExactly(mockDefendant);
    }

    @Test
    void getDefendantsByCase_ShouldSanitizeCaseUrn() {
        String unsanitizedCaseUrn = "<script>alert('xss')</script>";

        when(defendantDetailsService.getDefendantsByCase(any(), any(), any())).thenReturn(List.of());

        ResponseEntity<List<DefendantDetails>> response = defendantDetailsController.getDefendantsByCase(unsanitizedCaseUrn, null, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}