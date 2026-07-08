package uk.gov.hmcts.cp.mappers;

import org.junit.jupiter.api.Test;
import uk.gov.hmcts.cp.domain.ProgressionResponse.ProsecutionCase.Defendant;
import uk.gov.hmcts.cp.domain.ProgressionResponse.ProsecutionCase.PersonDefendant;
import uk.gov.hmcts.cp.domain.ProgressionResponse.ProsecutionCase.PersonDetails;
import uk.gov.hmcts.cp.openapi.model.DefendantDetails;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DefendantDetailsMapperTest {

    private final DefendantDetailsMapper defendantDetailsMapper = new DefendantDetailsMapper();

    @Test
    void full_defendant_should_map_all_fields() {
        UUID defendantId = UUID.randomUUID();
        UUID masterDefendantId = UUID.randomUUID();
        Defendant defendant = Defendant.builder()
                .id(defendantId)
                .masterDefendantId(masterDefendantId)
                .personDefendant(PersonDefendant.builder()
                        .personDetails(PersonDetails.builder()
                                .firstName("John")
                                .lastName("Doe")
                                .dateOfBirth(LocalDate.of(1980, 1, 31))
                                .build())
                        .build())
                .build();

        DefendantDetails result = defendantDetailsMapper.mapToDefendantDetails(defendant);

        assertThat(result.getDefendantId()).isEqualTo(defendantId);
        assertThat(result.getMasterDefendantId()).isEqualTo(masterDefendantId);
        assertThat(result.getName()).isEqualTo("John Doe");
        assertThat(result.getDateOfBirth()).isEqualTo(LocalDate.of(1980, 1, 31));
    }

    @Test
    void missing_personDefendant_should_return_null_name_and_dob() {
        UUID defendantId = UUID.randomUUID();
        Defendant defendant = Defendant.builder()
                .id(defendantId)
                .build();

        DefendantDetails result = defendantDetailsMapper.mapToDefendantDetails(defendant);

        assertThat(result.getDefendantId()).isEqualTo(defendantId);
        assertThat(result.getName()).isNull();
        assertThat(result.getDateOfBirth()).isNull();
    }

    @Test
    void missing_personDetails_should_return_null_name_and_dob() {
        Defendant defendant = Defendant.builder()
                .id(UUID.randomUUID())
                .personDefendant(PersonDefendant.builder().build())
                .build();

        DefendantDetails result = defendantDetailsMapper.mapToDefendantDetails(defendant);

        assertThat(result.getName()).isNull();
        assertThat(result.getDateOfBirth()).isNull();
    }

    @Test
    void missing_lastName_should_return_firstName_only() {
        Defendant defendant = Defendant.builder()
                .id(UUID.randomUUID())
                .personDefendant(PersonDefendant.builder()
                        .personDetails(PersonDetails.builder()
                                .firstName("John")
                                .build())
                        .build())
                .build();

        DefendantDetails result = defendantDetailsMapper.mapToDefendantDetails(defendant);

        assertThat(result.getName()).isEqualTo("John");
    }

    @Test
    void missing_firstName_and_lastName_should_return_null_name() {
        Defendant defendant = Defendant.builder()
                .id(UUID.randomUUID())
                .personDefendant(PersonDefendant.builder()
                        .personDetails(PersonDetails.builder().build())
                        .build())
                .build();

        DefendantDetails result = defendantDetailsMapper.mapToDefendantDetails(defendant);

        assertThat(result.getName()).isNull();
    }
}