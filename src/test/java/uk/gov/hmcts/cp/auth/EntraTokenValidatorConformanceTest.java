package uk.gov.hmcts.cp.auth;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static uk.gov.hmcts.cp.auth.TestTokens.AUDIENCE;
import static uk.gov.hmcts.cp.auth.TestTokens.CLIENT_ID;
import static uk.gov.hmcts.cp.auth.TestTokens.GRAPH_AUDIENCE;
import static uk.gov.hmcts.cp.auth.TestTokens.ISSUER;
import static uk.gov.hmcts.cp.auth.TestTokens.OBJECT_ID;
import static uk.gov.hmcts.cp.auth.TestTokens.REQUIRED_ROLE;
import static uk.gov.hmcts.cp.auth.TestTokens.SIBLING_API_AUDIENCE;
import static uk.gov.hmcts.cp.auth.TestTokens.SIGNING_KEY;
import static uk.gov.hmcts.cp.auth.TestTokens.TENANT_ID;
import static uk.gov.hmcts.cp.auth.TestTokens.UNRELATED_KEY;
import static uk.gov.hmcts.cp.auth.TestTokens.header;
import static uk.gov.hmcts.cp.auth.TestTokens.sign;
import static uk.gov.hmcts.cp.auth.TestTokens.validClaims;
import static uk.gov.hmcts.cp.auth.TestTokens.validToken;

/**
 * The per-repo conformance suite for Entra access token validation.
 */
class EntraTokenValidatorConformanceTest {

    private final EntraTokenValidator validator = TestTokens.validator();

    @Nested
    @DisplayName("Acceptance")
    class Acceptance {

        @Test
        void well_formed_app_only_token_is_accepted_and_yields_the_callers_client_id() throws Exception {
            final ValidatedCaller caller = validator.validate(validToken());

            assertThat(caller.clientId()).isEqualTo(CLIENT_ID);
            assertThat(caller.roles()).containsExactly(REQUIRED_ROLE);
        }

        /**
         * The highest-value regression test in the suite. Entra omits {@code idtyp} unless it is
         * explicitly enabled as an optional claim, so anyone who later "hardens" the app-only check
         * into requiring it takes the service down for every legitimate caller.
         */
        @Test
        void token_without_idtyp_is_accepted() throws Exception {
            final JWTClaimsSet claims = validClaims().build();
            assertThat(claims.getClaim("idtyp")).isNull();

            assertThat(validator.validate(sign(claims)).clientId()).isEqualTo(CLIENT_ID);
        }

        /**
         * Seeding a client registry with {@code oid} instead of {@code azp} produces a
         * signature-valid token that then 403s or 404s - nothing is wrong with the token, which
         * makes it a confusing failure to debug.
         */
        @Test
        void client_id_is_the_authorised_party_claim_never_the_object_id() throws Exception {
            final ValidatedCaller caller = validator.validate(validToken());

            assertThat(caller.clientId()).isEqualTo(CLIENT_ID);
            assertThat(caller.clientId()).hasToString(CLIENT_ID.toString());
            assertThat(caller.clientId().toString()).isNotEqualTo(OBJECT_ID);
        }

        @Test
        void expiry_inside_the_configured_clock_skew_is_tolerated() throws Exception {
            final String token = sign(validClaims()
                    .expirationTime(Date.from(Instant.now().minusSeconds(30)))
                    .build());

            assertThat(validator.validate(token).clientId()).isEqualTo(CLIENT_ID);
        }

        @Test
        void additional_roles_beyond_the_required_one_are_accepted_and_returned() throws Exception {
            final String token = sign(validClaims()
                    .claim("roles", List.of("Some.Other.Role", REQUIRED_ROLE))
                    .build());

            assertThat(validator.validate(token).roles())
                    .containsExactly("Some.Other.Role", REQUIRED_ROLE);
        }
    }

    @Nested
    @DisplayName("Signature - the attack cases")
    class Signature {

        @Test
        void unsigned_token_is_rejected() {
            final String unsigned = new PlainJWT(validClaims().build()).serialize();

            assertRejectedWith(unsigned, TokenRejectionReason.SIGNATURE_VERIFICATION_FAILED);
        }

        /**
         * Algorithm confusion, and the single most important negative test. The RSA public key is
         * published in the JWKS, so an attacker can take it and use it as an HMAC secret. A
         * validator that reads {@code alg} from the token header and verifies accordingly accepts
         * this. Pinning RS256 in the key selector means no candidate key is ever found.
         */
        @Test
        void hs256_signed_with_the_jwks_rsa_public_key_is_rejected() throws JOSEException {
            final byte[] publicKeyBytes = SIGNING_KEY.toRSAPublicKey().getEncoded();
            final SignedJWT jwt = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.HS256).keyID(SIGNING_KEY.getKeyID()).build(),
                    validClaims().build());
            jwt.sign(new MACSigner(publicKeyBytes));

            assertRejectedWith(jwt.serialize(), TokenRejectionReason.SIGNATURE_VERIFICATION_FAILED);
        }

        @Test
        void token_signed_by_an_unrelated_key_is_rejected() {
            final String token = sign(header(SIGNING_KEY.getKeyID()).build(),
                    validClaims().build(), UNRELATED_KEY);

            assertRejectedWith(token, TokenRejectionReason.SIGNATURE_VERIFICATION_FAILED);
        }

        @Test
        void tampered_signature_is_rejected() {
            final String token = validToken();
            final String body = token.substring(0, token.lastIndexOf('.') + 1);
            final String signature = token.substring(token.lastIndexOf('.') + 1);
            final String tampered = body + flipFirstCharacter(signature);

            assertRejectedWith(tampered, TokenRejectionReason.SIGNATURE_VERIFICATION_FAILED);
        }

        @Test
        void unknown_key_id_is_rejected() {
            final String token = sign(header("a-key-id-that-is-not-published").build(),
                    validClaims().build(), SIGNING_KEY);

            assertRejectedWith(token, TokenRejectionReason.SIGNATURE_VERIFICATION_FAILED);
        }

        @Test
        void unsupported_critical_header_is_rejected() {
            final String token = sign(
                    header(SIGNING_KEY.getKeyID())
                            .criticalParams(Set.of("https://example.test/unsupported"))
                            .build(),
                    validClaims().build(), SIGNING_KEY);

            assertRejectedWith(token, TokenRejectionReason.SIGNATURE_VERIFICATION_FAILED);
        }

        /**
         * The token cannot nominate its own verifying key. The key selector consults only the
         * configured JWKS, so {@code jku}, {@code jwk} and {@code x5u} in the header are ignored -
         * here the token is signed by a key it advertises in its own header, and is still rejected.
         */
        @Test
        void key_material_supplied_in_the_token_header_is_ignored() throws JOSEException {
            final String token = sign(
                    header(UNRELATED_KEY.getKeyID())
                            .jwk(UNRELATED_KEY.toPublicJWK())
                            .jwkURL(URI.create("https://attacker.test/jwks.json"))
                            .x509CertURL(URI.create("https://attacker.test/cert.pem"))
                            .build(),
                    validClaims().build(), UNRELATED_KEY);

            assertRejectedWith(token, TokenRejectionReason.SIGNATURE_VERIFICATION_FAILED);
        }

        private String flipFirstCharacter(final String signature) {
            final char first = signature.charAt(0);
            return (first == 'A' ? 'B' : 'A') + signature.substring(1);
        }
    }

    @Nested
    @DisplayName("Structure")
    class Structure {

        @Test
        void structurally_malformed_token_is_rejected() {
            assertRejectedWith("this-is-not-a-jwt", TokenRejectionReason.MALFORMED_TOKEN);
        }

        @Test
        void token_whose_payload_is_not_json_is_rejected() {
            final String token = validToken();
            final String[] parts = token.split("\\.");
            final String notJson = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString("not json".getBytes(StandardCharsets.UTF_8));

            assertRejectedWith(parts[0] + '.' + notJson + '.' + parts[2],
                    TokenRejectionReason.MALFORMED_TOKEN);
        }
    }

    @Nested
    @DisplayName("Audience and issuer")
    class AudienceAndIssuer {

        /** The standard onboarding mistake: requesting a Graph scope instead of this API's. */
        @Test
        void microsoft_graph_token_is_rejected_on_audience() {
            assertRejectedWith(sign(validClaims().audience(GRAPH_AUDIENCE).build()),
                    TokenRejectionReason.CLAIM_VALIDATION_FAILED);
        }

        /** The spike-GUID mistake. Never "fixed" by widening the accepted audience. */
        @Test
        void token_minted_for_a_sibling_cp_api_is_rejected_on_audience() {
            assertRejectedWith(sign(validClaims().audience(SIBLING_API_AUDIENCE).build()),
                    TokenRejectionReason.CLAIM_VALIDATION_FAILED);
        }

        @Test
        void missing_audience_is_rejected() {
            assertRejectedWith(sign(validClaims().audience((String) null).build()),
                    TokenRejectionReason.CLAIM_VALIDATION_FAILED);
        }

        @Test
        void wrong_issuer_is_rejected() {
            assertRejectedWith(sign(validClaims().issuer("https://issuer.attacker.test/v2.0").build()),
                    TokenRejectionReason.CLAIM_VALIDATION_FAILED);
        }

        /** A prefix or contains match would admit this. The issuer is matched exactly. */
        @Test
        void issuer_that_merely_starts_with_the_expected_value_is_rejected() {
            assertRejectedWith(sign(validClaims().issuer(ISSUER + ".attacker.example").build()),
                    TokenRejectionReason.CLAIM_VALIDATION_FAILED);
        }
    }

    @Nested
    @DisplayName("Time and tenancy")
    class TimeAndTenancy {

        @Test
        void expired_token_is_rejected() {
            assertRejectedWith(
                    sign(validClaims().expirationTime(Date.from(Instant.now().minusSeconds(3600))).build()),
                    TokenRejectionReason.CLAIM_VALIDATION_FAILED);
        }

        /** {@code exp} is required, not merely checked when present. */
        @Test
        void token_without_exp_is_rejected() {
            assertRejectedWith(sign(validClaims().expirationTime(null).build()),
                    TokenRejectionReason.CLAIM_VALIDATION_FAILED);
        }

        @Test
        void not_yet_valid_token_is_rejected() {
            assertRejectedWith(
                    sign(validClaims().notBeforeTime(Date.from(Instant.now().plusSeconds(3600))).build()),
                    TokenRejectionReason.CLAIM_VALIDATION_FAILED);
        }

        @Test
        void wrong_tenant_id_is_rejected() {
            assertRejectedWith(
                    sign(validClaims().claim("tid", "99999999-9999-9999-9999-999999999999").build()),
                    TokenRejectionReason.CLAIM_VALIDATION_FAILED);
        }

        /** Already rejected on issuer; this records the decision not to build the v1.0 path. */
        @Test
        void v1_token_is_rejected() {
            assertRejectedWith(
                    sign(validClaims()
                            .issuer("https://sts.windows.net/" + TENANT_ID + "/")
                            .claim("ver", "1.0")
                            .build()),
                    TokenRejectionReason.CLAIM_VALIDATION_FAILED);
        }

        @Test
        void unexpected_token_version_is_rejected() {
            assertRejectedWith(sign(validClaims().claim("ver", "3.0").build()),
                    TokenRejectionReason.CLAIM_VALIDATION_FAILED);
        }
    }

    @Nested
    @DisplayName("Identity and app-only")
    class IdentityAndAppOnly {

        @Test
        void missing_authorised_party_claim_is_rejected() {
            assertRejectedWith(sign(validClaims().claim("azp", null).build()),
                    TokenRejectionReason.MISSING_AUTHORISED_PARTY);
        }

        @Test
        void non_uuid_authorised_party_claim_is_rejected() {
            assertRejectedWith(sign(validClaims().claim("azp", "not-a-uuid").build()),
                    TokenRejectionReason.INVALID_AUTHORISED_PARTY);
        }

        /** For a delegated token {@code sub} identifies the user, so it differs from {@code oid}. */
        @Test
        void delegated_token_is_rejected_on_sub_not_equal_to_oid() {
            assertRejectedWith(
                    sign(validClaims().subject("66666666-6666-6666-6666-666666666666").build()),
                    TokenRejectionReason.NOT_APP_ONLY_TOKEN);
        }

        /** The presence of {@code scp} means a delegated user token. */
        @Test
        void token_carrying_scp_is_rejected() {
            assertRejectedWith(sign(validClaims().claim("scp", "defendant.read").build()),
                    TokenRejectionReason.CLAIM_VALIDATION_FAILED);
        }

        /**
         * Roles declared on the app registration but never assigned with admin consent produce a
         * token that looks entirely correct and silently omits {@code roles}.
         */
        @Test
        void token_without_roles_is_rejected() {
            assertRejectedWith(sign(validClaims().claim("roles", null).build()),
                    TokenRejectionReason.MISSING_ROLES);
        }

        @Test
        void token_with_an_empty_roles_array_is_rejected() {
            assertRejectedWith(sign(validClaims().claim("roles", List.of()).build()),
                    TokenRejectionReason.MISSING_ROLES);
        }

        @Test
        void token_without_the_required_role_is_rejected_as_forbidden() {
            assertRejectedWith(sign(validClaims().claim("roles", List.of("Some.Other.Role")).build()),
                    TokenRejectionReason.INSUFFICIENT_ROLE);
        }
    }

    @Nested
    @DisplayName("Error semantics and leakage")
    class ErrorSemanticsAndLeakage {

        /**
         * The exception reaches the client through a {@code WWW-Authenticate} header and an error
         * body, and reaches operators through logs. Nimbus's own messages embed claim values taken
         * from the token, so neither the token nor the underlying exception may be carried.
         */
        @Test
        void exception_never_carries_token_material() {
            final String token = sign(validClaims().audience(GRAPH_AUDIENCE).build());

            final TokenValidationException thrown = catchThrowableOfType(
                    TokenValidationException.class, () -> validator.validate(token));

            assertThat(thrown).isNotNull();
            assertThat(thrown.getMessage()).doesNotContain(token, GRAPH_AUDIENCE, AUDIENCE);
            assertThat(thrown.toString()).doesNotContain(token, GRAPH_AUDIENCE, AUDIENCE);
            assertThat(thrown.getCause()).isNull();
            assertThat(thrown.getSuppressed()).isEmpty();
        }

        @Test
        void a_rejected_signature_is_unauthorised_and_a_wrong_role_is_forbidden() {
            assertThat(TokenRejectionReason.SIGNATURE_VERIFICATION_FAILED.getStatus().value()).isEqualTo(401);
            assertThat(TokenRejectionReason.SIGNATURE_VERIFICATION_FAILED.getErrorCode()).isEqualTo("invalid_token");
            assertThat(TokenRejectionReason.INSUFFICIENT_ROLE.getStatus().value()).isEqualTo(403);
            assertThat(TokenRejectionReason.INSUFFICIENT_ROLE.getErrorCode()).isEqualTo("insufficient_scope");
        }

        /** RFC 6750 section 3: a request carrying no credentials gets a challenge with no error code. */
        @Test
        void a_missing_authorization_header_yields_a_challenge_with_no_error_code() {
            assertThat(TokenRejectionReason.MISSING_AUTHORIZATION_HEADER.getStatus().value()).isEqualTo(401);
            assertThat(TokenRejectionReason.MISSING_AUTHORIZATION_HEADER.getErrorCode()).isNull();
        }
    }

    @Nested
    @DisplayName("Non-enforcing modes")
    class NonEnforcingModes {

        /**
         * OFF and OBSERVE do not weaken the validator itself - it still validates. What they change
         * is whether the filter acts on the result, which {@code AuthenticationFilterTest} covers.
         */
        @Test
        void the_validator_validates_identically_whatever_the_mode() {
            final EntraTokenValidator observing =
                    new EntraTokenValidator(TestTokens.properties(AuthMode.OBSERVE), TestTokens.jwkSource());

            assertThatThrownBy(() -> observing.validate(sign(validClaims().audience(GRAPH_AUDIENCE).build())))
                    .isInstanceOf(TokenValidationException.class);
        }
    }

    private void assertRejectedWith(final String token, final TokenRejectionReason expected) {
        final TokenValidationException thrown = catchThrowableOfType(
                TokenValidationException.class, () -> validator.validate(token));

        assertThat(thrown)
                .withFailMessage("Expected the token to be rejected with %s, but it was accepted", expected)
                .isNotNull();
        assertThat(thrown.getReason()).isEqualTo(expected);
    }
}
