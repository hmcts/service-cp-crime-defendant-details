package uk.gov.hmcts.cp.integration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import jakarta.annotation.Resource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import uk.gov.hmcts.cp.auth.TestTokens;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The protected endpoint, through the real filter chain, with enforcement on and no default token.
 *
 * <p>{@code EntraTokenValidatorConformanceTest} proves the validator rejects each class of bad
 * token. This proves the service does - that the filter is actually registered, ahead of the
 * handler, and that a rejection comes back in the shape the contract declares.
 */
@SpringBootTest(properties = {
    "auth.mode=ENFORCE",
    "auth.tenant-id=" + TestTokens.TENANT_ID,
    "auth.audience=" + TestTokens.AUDIENCE,
    "auth.required-role=" + TestTokens.REQUIRED_ROLE,
    "auth.clock-skew-seconds=60"})
@AutoConfigureMockMvc
@Import(TestAuthConfiguration.class)
@SuppressWarnings("PMD.UnitTestShouldIncludeAssert") // MockMvc andExpect() calls are assertions
class AuthenticationIntegrationTest {

    private static final String PROTECTED_PATH = "/defendants/cases/20GD1234567";
    private static final String UNKNOWN_PATH = "/defendants/cases/20GD1234567/does-not-exist";

    @Resource
    private MockMvc mockMvc;

    @Test
    void the_protected_endpoint_rejects_a_request_with_no_token() throws Exception {
        mockMvc.perform(get(PROTECTED_PATH).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("Access token is missing"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "Bearer not-a-jwt",
        "Basic dXNlcjpwYXNz",
        "Bearer ",
        ""})
    void the_protected_endpoint_rejects_an_invalid_credential(final String authorization) throws Exception {
        mockMvc.perform(get(PROTECTED_PATH)
                        .header("Authorization", authorization)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", org.hamcrest.Matchers.startsWith("Bearer")));
    }

    /** A signature-valid token for the wrong resource is still rejected, by this service. */
    @Test
    void the_protected_endpoint_rejects_a_token_minted_for_another_api() throws Exception {
        final String token = TestTokens.sign(TestTokens.validClaims()
                .audience(TestTokens.SIBLING_API_AUDIENCE)
                .build());

        mockMvc.perform(get(PROTECTED_PATH)
                        .header("Authorization", "Bearer " + token)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate",
                        org.hamcrest.Matchers.containsString("invalid_token")));
    }

    @Test
    void the_protected_endpoint_forbids_a_token_without_the_required_role() throws Exception {
        final String token = TestTokens.sign(TestTokens.validClaims()
                .claim("roles", List.of("Some.Other.Role"))
                .build());

        mockMvc.perform(get(PROTECTED_PATH)
                        .header("Authorization", "Bearer " + token)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"))
                .andExpect(header().string("WWW-Authenticate",
                        org.hamcrest.Matchers.containsString("insufficient_scope")));
    }

    @Test
    void an_unknown_path_is_rejected_before_it_can_be_probed() throws Exception {
        mockMvc.perform(get(UNKNOWN_PATH))
                .andExpect(status().isUnauthorized());
    }

    /**
     * <p>404 rather than 200 because the path has no handler: the request got past authentication,
     * which is the whole assertion.
     */
    @Test
    void a_valid_token_is_accepted_against_the_in_process_key_set() throws Exception {
        mockMvc.perform(get(UNKNOWN_PATH)
                        .header("Authorization", "Bearer " + TestTokens.validToken()))
                .andExpect(status().isNotFound());
    }
}
