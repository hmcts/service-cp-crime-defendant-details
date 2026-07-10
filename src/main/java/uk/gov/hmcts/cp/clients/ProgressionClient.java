package uk.gov.hmcts.cp.clients;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.owasp.encoder.Encode;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import uk.gov.hmcts.cp.config.AppPropertiesBackend;
import uk.gov.hmcts.cp.domain.ProgressionResponse;

import java.util.UUID;

@Component
@Primary
@RequiredArgsConstructor
@Slf4j
public class ProgressionClient {

    private final AppPropertiesBackend appProperties;
    private final RestClient restClient;

    public ProgressionResponse getProgressionResponse(final UUID caseId) {
        final String url = buildUrl(caseId);
        log.info("Getting hearings from {}", Encode.forJava(url));
        return restClient.get()
                .uri(url)
                .header("Accept", "application/vnd.progression.query.prosecutioncase+json")
                .header("CJSCPPUID", appProperties.getProgressionCjscppuid())
                .retrieve()
                .body(ProgressionResponse.class);
    }

    private String buildUrl(final UUID caseId) {
        return String.format("%s%s/%s", appProperties.getProgressionUrl(), appProperties.getProgressionPath(), caseId);
    }
}
