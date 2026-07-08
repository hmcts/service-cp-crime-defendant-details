package uk.gov.hmcts.cp.controllers;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.owasp.encoder.Encode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.cp.openapi.api.DefendantsApi;
import uk.gov.hmcts.cp.openapi.model.DefendantDetails;
import uk.gov.hmcts.cp.services.DefendantDetailsService;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Slf4j
public class DefendantDetailsController implements DefendantsApi {

    private final DefendantDetailsService defendantDetailsService;

    @Override
    @NonNull
    public ResponseEntity<List<DefendantDetails>> getDefendantsByCase(final String caseURN, final UUID masterDefendantId, final UUID defendantId) {
        final String sanitizedCaseUrn = Encode.forJava(caseURN);
        log.info("Received request to get defendant details for caseUrn:{}", sanitizedCaseUrn);
        final List<DefendantDetails> defendantDetails = defendantDetailsService.getDefendantsByCase(sanitizedCaseUrn, masterDefendantId, defendantId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(defendantDetails);
    }
}