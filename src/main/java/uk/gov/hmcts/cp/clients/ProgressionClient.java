package uk.gov.hmcts.cp.clients;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.owasp.encoder.Encode;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import uk.gov.hmcts.cp.config.AppPropertiesBackend;
import uk.gov.hmcts.cp.domain.ProgressionResponse;

import java.util.UUID;

@Component
@Primary
@RequiredArgsConstructor
@Slf4j
public class ProgressionClient {

    private final AppPropertiesBackend appProperties;
    private final RestTemplate restTemplate;

    public ProgressionResponse getProgressionResponse(final UUID caseId) {
        final String url = buildUrl(caseId);
        log.info("Getting hearings from {}", Encode.forJava(url));
        final ResponseEntity<ProgressionResponse> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                getRequestEntity(),
                ProgressionResponse.class
        );
        return response.getBody();
    }

    private String buildUrl(final UUID caseId) {
        return String.format("%s%s/%s", appProperties.getProgressionUrl(), appProperties.getProgressionPath(), caseId);
    }

    private HttpEntity<String> getRequestEntity() {
        final HttpHeaders headers = new HttpHeaders();
        headers.add("Accept", "application/vnd.progression.query.prosecutioncase+json");
        headers.add("CJSCPPUID", appProperties.getProgressionCjscppuid());
        return new HttpEntity<>(headers);
    }
}
