package uk.gov.hmcts.cp.clients;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.owasp.encoder.Encode;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import uk.gov.hmcts.cp.config.AppPropertiesBackend;
import uk.gov.hmcts.cp.openapi.model.CaseMapperResponse;

@Component
@Primary
@RequiredArgsConstructor
@Slf4j
public class CaseUrnMapperClient {
    private final AppPropertiesBackend appProperties;
    private final RestClient restClient;

    public CaseMapperResponse getCaseMapping(final String caseUrn) {
        final String sanitizedCaseUrn = Encode.forJava(caseUrn);
        final String url = getCaseIdUrl(caseUrn);
        log.info("Getting caseId from {}", url);
        final CaseMapperResponse response = restClient.get()
                .uri(url)
                .retrieve()
                .body(CaseMapperResponse.class);
        log.info("CaseMapperResponse returning caseId:{} for caseUrn:{}", response.getCaseId(), sanitizedCaseUrn);
        return response;
    }

    private String getCaseIdUrl(final String caseUrn) {
        return String.format("%s%s/%s", appProperties.getCaseMapperUrl(), appProperties.getCaseMapperPath(), caseUrn);
    }
}