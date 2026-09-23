package uk.gov.hmcts.cp.integration;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import uk.gov.hmcts.cp.auth.TestTokens;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Attaches a valid bearer token to every MockMvc request by default.
 *
 * <p>Imported only by {@link IntegrationTestBase}, whose tests are about the service's behaviour
 * rather than about authentication - they still run through the real authentication filter with a
 * real token that is really verified, but they do not have to restate it on every request. Tests
 * that are about authentication supply their own header, and {@link ActuatorIntegrationTest}
 * deliberately does not import this so it can prove the exempt endpoints need no token at all.
 */
@TestConfiguration
public class AuthenticatedMockMvcConfiguration {

    @Bean
    public MockMvcBuilderCustomizer authenticatedByDefault() {
        return builder -> builder.defaultRequest(
                get("/").header("Authorization", "Bearer " + TestTokens.validToken()));
    }
}
