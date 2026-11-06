package uk.gov.hmcts.cp.config;

import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import uk.gov.hmcts.cp.auth.AuthProperties;
import uk.gov.hmcts.cp.auth.JwkSourceFactory;

import java.net.MalformedURLException;
import java.net.URI;
import java.time.Duration;

/**
 * The Entra key sets used to verify access token signatures, one per trusted issuer.
 */
@Configuration
public class AuthConfig {

    /**
     * How long a cached key set stays usable past its refresh time during an outage. Entra keys
     * roll infrequently, so tolerating a long outage costs little and avoids a total loss of
     * service on a dependency this service cannot influence.
     */
    private static final Duration OUTAGE_TOLERANCE = Duration.ofHours(1);

    private static final Duration REFRESH_TIMEOUT = Duration.ofSeconds(30);

    @Bean
    public JwkSourceFactory entraJwkSourceFactory(final AuthProperties authProperties) {
        final long cacheTtlMs = Duration.ofSeconds(authProperties.getJwksCacheTtlSeconds()).toMillis();
        return jwksUri -> {
            try {
                return JWKSourceBuilder.create(URI.create(jwksUri).toURL())
                        .cache(cacheTtlMs, REFRESH_TIMEOUT.toMillis())
                        .refreshAheadCache(true)
                        .rateLimited(true)
                        .retrying(true)
                        .outageTolerant(OUTAGE_TOLERANCE.toMillis())
                        .build();
            } catch (MalformedURLException e) {
                throw new IllegalStateException("Invalid JWKS URI: " + jwksUri, e);
            }
        };
    }
}
