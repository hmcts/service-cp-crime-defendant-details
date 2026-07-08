package uk.gov.hmcts.cp.mappers;

import org.springframework.stereotype.Component;
import uk.gov.hmcts.cp.domain.ProgressionResponse;
import uk.gov.hmcts.cp.openapi.model.DefendantDetails;

import java.time.LocalDate;
import java.util.Optional;

@Component
public class DefendantDetailsMapper {

    public DefendantDetails mapToDefendantDetails(final ProgressionResponse.ProsecutionCase.Defendant defendant) {
        final Optional<ProgressionResponse.ProsecutionCase.PersonDetails> personDetails = Optional.ofNullable(defendant)
                .map(ProgressionResponse.ProsecutionCase.Defendant::getPersonDefendant)
                .map(ProgressionResponse.ProsecutionCase.PersonDefendant::getPersonDetails);

        final String name = personDetails
                .map(this::toFullName)
                .orElse(null);

        final LocalDate dateOfBirth = personDetails
                .map(ProgressionResponse.ProsecutionCase.PersonDetails::getDateOfBirth)
                .orElse(null);

        return DefendantDetails.builder()
                .defendantId(defendant.getId())
                .masterDefendantId(defendant.getMasterDefendantId())
                .name(name)
                .dateOfBirth(dateOfBirth)
                .build();
    }

    private String toFullName(final ProgressionResponse.ProsecutionCase.PersonDetails personDetails) {
        final String firstName = Optional.ofNullable(personDetails.getFirstName()).orElse("");
        final String lastName = Optional.ofNullable(personDetails.getLastName()).orElse("");
        final String fullName = (firstName + " " + lastName).trim();
        return fullName.isEmpty() ? null : fullName;
    }
}