package uk.gov.hmcts.cp.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Getter
public class AppPropertiesBackend {

    private final String caseMapperUrl;
    private final String caseMapperPath;
    private final String progressionUrl;
    private final String progressionPath;
    private final String progressionCjscppuid;

    public AppPropertiesBackend(
            @Value("${case-mapper-client.url}") final String caseMapperUrl,
            @Value("${case-mapper-client.path}") final String caseMapperPath,
            @Value("${progression-client.url}") final String progressionUrl,
            @Value("${progression-client.path}") final String progressionPath,
            @Value("${progression-client.cjscppuid}") final String progressionCjscppuid) {
        this.caseMapperUrl = caseMapperUrl;
        this.caseMapperPath = caseMapperPath;
        this.progressionUrl = progressionUrl;
        this.progressionPath = progressionPath;
        this.progressionCjscppuid = progressionCjscppuid;
    }
}