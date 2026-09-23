package uk.gov.hmcts.cp.config;

import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import uk.gov.hmcts.cp.auth.AuthProperties;

import java.net.MalformedURLException;
import java.net.URI;
import java.time.Duration;

/**
 * The Entra key set used to verify access token signatures.
 */
@Configuration
public class AuthConfig {

    /**
     * How long a cached key set stays usable past its refresh time during an outage. Entra keys
     * roll infrequently, so tolerating a long outage costs little and avoids a total loss of
     * service on a dependency this service cannot influence.
     */
    private static final Duration OUTAGE_TOLERANCE = Duration.ofHours(1);

    @Bean
    public JWKSource<SecurityContext> entraJwkSource(final AuthProperties authProperties)
            throws MalformedURLException {
        return JWKSourceBuilder.create(URI.create(authProperties.getJwksUri()).toURL())
                .cache(Duration.ofSeconds(authProperties.getJwksCacheTtlSeconds()).toMillis(),
                        Duration.ofSeconds(30).toMillis())
                .refreshAheadCache(true)
                .rateLimited(true)
                .retrying(true)
                .outageTolerant(OUTAGE_TOLERANCE.toMillis())
                .build();
    }
}
