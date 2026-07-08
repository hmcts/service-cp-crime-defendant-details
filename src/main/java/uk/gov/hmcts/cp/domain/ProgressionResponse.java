package uk.gov.hmcts.cp.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
public class ProgressionResponse {
    private ProsecutionCase prosecutionCase;

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @Getter
    public static class ProsecutionCase {
        private List<Defendant> defendants;

        @JsonIgnoreProperties(ignoreUnknown = true)
        @Builder
        @AllArgsConstructor
        @NoArgsConstructor
        @Getter
        public static class Defendant {
            private UUID id;
            private UUID masterDefendantId;
            private PersonDefendant personDefendant;
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        @Builder
        @AllArgsConstructor
        @NoArgsConstructor
        @Getter
        public static class PersonDefendant {
            private PersonDetails personDetails;
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        @Builder
        @AllArgsConstructor
        @NoArgsConstructor
        @Getter
        public static class PersonDetails {
            private String firstName;
            private String lastName;
            private LocalDate dateOfBirth;
        }
    }

}