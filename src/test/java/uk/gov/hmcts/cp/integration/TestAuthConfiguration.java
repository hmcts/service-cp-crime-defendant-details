package uk.gov.hmcts.cp.integration;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import uk.gov.hmcts.cp.auth.JwkSourceFactory;
import uk.gov.hmcts.cp.auth.TestTokens;

/**
 * Supplies the signing key in-process as the application's key set, so integration tests run with
 * enforcement genuinely on rather than with validation disabled.
 *
 */
@TestConfiguration
public class TestAuthConfiguration {

    @Bean
    @Primary
    public JwkSourceFactory testEntraJwkSourceFactory() {
        return TestTokens.jwkSourceFactory();
    }
}
