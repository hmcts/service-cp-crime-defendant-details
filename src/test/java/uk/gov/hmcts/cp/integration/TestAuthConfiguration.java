package uk.gov.hmcts.cp.integration;

import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
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
    public JWKSource<SecurityContext> testEntraJwkSource() {
        return TestTokens.jwkSource();
    }
}
