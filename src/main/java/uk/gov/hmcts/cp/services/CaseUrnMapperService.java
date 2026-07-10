package uk.gov.hmcts.cp.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.cp.clients.CaseUrnMapperClient;
import uk.gov.hmcts.cp.openapi.model.CaseMapperResponse;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CaseUrnMapperService {
    private final CaseUrnMapperClient caseUrnMapperClient;

    public UUID getCaseId(final String caseUrn) {
        log.info("Fetching caseId for caseUrn:{}", caseUrn);
        final CaseMapperResponse response = caseUrnMapperClient.getCaseMapping(caseUrn);
        return response.getCaseId();
    }
}