package uk.gov.hmcts.cp.auth;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jwt.JWTClaimsSet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.Map;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static uk.gov.hmcts.cp.auth.TestTokens.ACCEPTED_ROLES;
import static uk.gov.hmcts.cp.auth.TestTokens.AUDIENCE;
import static uk.gov.hmcts.cp.auth.TestTokens.CLIENT_ID;
import static uk.gov.hmcts.cp.auth.TestTokens.CLOCK_SKEW_SECONDS;
import static uk.gov.hmcts.cp.auth.TestTokens.ISSUER;
import static uk.gov.hmcts.cp.auth.TestTokens.TENANT_ID;
import static uk.gov.hmcts.cp.auth.TestTokens.header;
import static uk.gov.hmcts.cp.auth.TestTokens.validClaims;

/** One pod trusting primary and CP: each tenant has its own key, so issuers must never mix. */
class EntraTokenValidatorDualIssuerTest {

    private static final String CP_TENANT = "66666666-6666-6666-6666-666666666666";
    private static final String CP_AUDIENCE = "77777777-7777-7777-7777-777777777777";
    private static final String CP_ISSUER = issuerFor(CP_TENANT);

    /** Same kid in both tenants on purpose: a key must be found by issuer, never by kid alone. */
    private static final String KEY_ID = "shared-kid";

    private static RSAKey primaryKey;
    private static RSAKey cpKey;
    private static EntraTokenValidator dualValidator;
    private static EntraTokenValidator primaryOnlyValidator;

    @BeforeAll
    static void setUp() throws JOSEException {
        primaryKey = new RSAKeyGenerator(2048).keyID(KEY_ID).keyUse(KeyUse.SIGNATURE).generate();
        cpKey = new RSAKeyGenerator(2048).keyID(KEY_ID).keyUse(KeyUse.SIGNATURE).generate();
        final Map<String, JWKSource<SecurityContext>> keysByUri = Map.of(
                jwksUriFor(TENANT_ID), new ImmutableJWKSet<>(new JWKSet(primaryKey.toPublicJWK())),
                jwksUriFor(CP_TENANT), new ImmutableJWKSet<>(new JWKSet(cpKey.toPublicJWK())));
        final JwkSourceFactory factory = keysByUri::get;

        dualValidator = new EntraTokenValidator(properties(CP_TENANT, CP_AUDIENCE), factory);
        primaryOnlyValidator = new EntraTokenValidator(properties("", ""), factory);
    }

    @Test
    void a_primary_token_for_the_primary_audience_is_accepted_and_labelled_primary() throws Exception {
        final ValidatedCaller caller = dualValidator.validate(primaryToken(claims -> { }));

        assertThat(caller.clientId()).isEqualTo(CLIENT_ID);
        assertThat(caller.issuerName()).isEqualTo("primary");
    }

    @Test
    void a_cp_token_for_the_cp_audience_is_accepted_and_labelled_cp() throws Exception {
        final ValidatedCaller caller = dualValidator.validate(cpToken(claims -> { }));

        assertThat(caller.clientId()).isEqualTo(CLIENT_ID);
        assertThat(caller.issuerName()).isEqualTo("cp");
    }

    @Test
    void a_same_tenant_cp_audience_is_accepted_and_labelled_primary_plus_cp() throws Exception {
        final EntraTokenValidator merged = new EntraTokenValidator(properties(TENANT_ID, CP_AUDIENCE),
                jwksUri -> new ImmutableJWKSet<>(new JWKSet(primaryKey.toPublicJWK())));

        assertThat(merged.validate(primaryToken(claims -> claims.audience(CP_AUDIENCE))).issuerName())
                .isEqualTo("primary+cp");
        assertThat(merged.validate(primaryToken(claims -> { })).issuerName()).isEqualTo("primary+cp");
    }

    @Test
    void a_cp_token_minted_for_the_primary_audience_is_rejected() {
        assertRejected(dualValidator, cpToken(claims -> claims.audience(AUDIENCE)),
                TokenRejectionReason.CLAIM_VALIDATION_FAILED);
    }

    @Test
    void a_primary_token_minted_for_the_cp_audience_is_rejected() {
        assertRejected(dualValidator, primaryToken(claims -> claims.audience(CP_AUDIENCE)),
                TokenRejectionReason.CLAIM_VALIDATION_FAILED);
    }

    @Test
    void a_token_claiming_the_cp_issuer_but_signed_with_the_primary_key_is_rejected() {
        assertRejected(dualValidator, sign(primaryKey, cpClaims()), TokenRejectionReason.SIGNATURE_VERIFICATION_FAILED);
    }

    @Test
    void a_token_claiming_the_primary_issuer_but_signed_with_the_cp_key_is_rejected() {
        assertRejected(dualValidator, sign(cpKey, validClaims()), TokenRejectionReason.SIGNATURE_VERIFICATION_FAILED);
    }

    @Test
    void the_cp_issuer_with_the_primary_tenant_id_is_rejected() {
        assertRejected(dualValidator, cpToken(claims -> claims.claim("tid", TENANT_ID)),
                TokenRejectionReason.CLAIM_VALIDATION_FAILED);
    }

    @Test
    void an_issuer_that_is_neither_primary_nor_cp_is_rejected() {
        final String other = issuerFor("99999999-9999-9999-9999-999999999999");

        assertRejected(dualValidator, primaryToken(claims -> claims.issuer(other)),
                TokenRejectionReason.CLAIM_VALIDATION_FAILED);
    }

    @Test
    void a_token_with_no_issuer_is_rejected() {
        assertRejected(dualValidator, primaryToken(claims -> claims.issuer(null)),
                TokenRejectionReason.CLAIM_VALIDATION_FAILED);
    }

    @Test
    void a_genuine_cp_token_is_rejected_when_cp_is_not_configured() {
        assertRejected(primaryOnlyValidator, cpToken(claims -> { }), TokenRejectionReason.CLAIM_VALIDATION_FAILED);
    }

    private static void assertRejected(final EntraTokenValidator validator, final String token,
                                       final TokenRejectionReason expected) {
        assertThatThrownBy(() -> validator.validate(token))
                .isInstanceOfSatisfying(TokenValidationException.class,
                        e -> assertThat(e.getReason()).isEqualTo(expected));
    }

    private static AuthProperties properties(final String cpTenant, final String cpAudience) {
        final MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("test");
        return new AuthProperties(environment, AuthMode.ENFORCE, TENANT_ID, AUDIENCE, "", "",
                cpTenant, cpAudience, "", "", ACCEPTED_ROLES, CLOCK_SKEW_SECONDS, 300);
    }

    private static String primaryToken(final Consumer<JWTClaimsSet.Builder> customiser) {
        final JWTClaimsSet.Builder claims = validClaims();
        customiser.accept(claims);
        return sign(primaryKey, claims);
    }

    private static String cpToken(final Consumer<JWTClaimsSet.Builder> customiser) {
        final JWTClaimsSet.Builder claims = cpClaims();
        customiser.accept(claims);
        return sign(cpKey, claims);
    }

    private static JWTClaimsSet.Builder cpClaims() {
        return validClaims().issuer(CP_ISSUER).claim("tid", CP_TENANT).audience(CP_AUDIENCE);
    }

    private static String sign(final RSAKey key, final JWTClaimsSet.Builder claims) {
        return TestTokens.sign(header(KEY_ID).build(), claims.build(), key);
    }

    private static String issuerFor(final String tenant) {
        return ISSUER.replace(TENANT_ID, tenant);
    }

    private static String jwksUriFor(final String tenant) {
        return "https://login.microsoftonline.com/" + tenant + "/discovery/v2.0/keys";
    }
}
