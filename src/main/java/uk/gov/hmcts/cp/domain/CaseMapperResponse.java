package uk.gov.hmcts.cp.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
public class CaseMapperResponse {

    private UUID caseId;

    private String caseUrn;

}