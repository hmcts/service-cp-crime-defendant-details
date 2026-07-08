package uk.gov.hmcts.cp.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.hmcts.cp.clients.ProgressionClient;
import uk.gov.hmcts.cp.domain.ProgressionResponse;
import uk.gov.hmcts.cp.mappers.DefendantDetailsMapper;
import uk.gov.hmcts.cp.openapi.model.DefendantDetails;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DefendantDetailsService {

    private final CaseUrnMapperService caseUrnMapperService;
    private final ProgressionClient progressionClient;
    private final DefendantDetailsMapper defendantDetailsMapper;

    public List<DefendantDetails> getDefendantsByCase(final String caseUrn, final UUID masterDefendantId, final UUID defendantId) {
        final UUID caseId = caseUrnMapperService.getCaseId(caseUrn);
        final ProgressionResponse progressionResponse = progressionClient.getProgressionResponse(caseId);
        final ProgressionResponse.ProsecutionCase prosecutionCase = progressionResponse == null ? null : progressionResponse.getProsecutionCase();
        validateOrThrowError(prosecutionCase, HttpStatus.NOT_FOUND, "No case found for the supplied case URN");

        return Optional.ofNullable(prosecutionCase.getDefendants())
                .orElse(Collections.emptyList())
                .stream()
                .filter(Objects::nonNull)
                .filter(defendant -> masterDefendantId == null || masterDefendantId.equals(defendant.getMasterDefendantId()))
                .filter(defendant -> defendantId == null || defendantId.equals(defendant.getId()))
                .map(defendantDetailsMapper::mapToDefendantDetails)
                .toList();
    }

    private void validateOrThrowError(final Object obj, final HttpStatus status, final String errorMessage) {
        if (ObjectUtils.isEmpty(obj)) {
            log.error(errorMessage);
            throw new ResponseStatusException(status, errorMessage);
        }
    }
}