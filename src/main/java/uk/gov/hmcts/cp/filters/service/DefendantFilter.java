package uk.gov.hmcts.cp.filters.service;

import org.springframework.stereotype.Component;
import uk.gov.hmcts.cp.domain.ProgressionResponse.ProsecutionCase.Defendant;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Component
public class DefendantFilter {

    public List<Defendant> filter(final List<Defendant> defendants, final UUID masterDefendantId, final UUID defendantId) {
        return Optional.ofNullable(defendants)
                .orElse(Collections.emptyList())
                .stream()
                .filter(Objects::nonNull)
                .filter(defendant -> masterDefendantId == null || masterDefendantId.equals(defendant.getMasterDefendantId()))
                .filter(defendant -> defendantId == null || defendantId.equals(defendant.getId()))
                .toList();
    }
}