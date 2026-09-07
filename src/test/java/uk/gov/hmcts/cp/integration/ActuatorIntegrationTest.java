package uk.gov.hmcts.cp.integration;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import uk.gov.hmcts.cp.auth.TestTokens;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "auth.mode=ENFORCE",
    "auth.tenant-id=" + TestTokens.TENANT_ID,
    "auth.audience=" + TestTokens.AUDIENCE,
    "auth.required-role=" + TestTokens.REQUIRED_ROLE,
    "auth.clock-skew-seconds=60"})
@AutoConfigureMockMvc
@Import(TestAuthConfiguration.class)
@SuppressWarnings("PMD.UnitTestShouldIncludeAssert") // MockMvc andExpect() calls are assertions
class ActuatorIntegrationTest {

    @Resource
    private MockMvc mockMvc;

    @Test
    void actuator_info_should_have_build_fields() throws Exception {
        final String name = "service-cp-crime-defendant-details";
        mockMvc.perform(get("/actuator/info"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.build.artifact").value(name))
                .andExpect(jsonPath("$.build.name").value(name))
                .andExpect(jsonPath("$.build.time").exists())
                .andExpect(jsonPath("$.build.version").exists());
    }

    @Test
    void actuator_info_should_have_gorylenko_git_fields() throws Exception {
        mockMvc.perform(get("/actuator/info"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.git.branch").exists())
                .andExpect(jsonPath("$.git.commit.id").exists())
                .andExpect(jsonPath("$.git.commit.time").exists());
    }

    @Test
    void actuator_health_should_have_correct_fields() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.groups[0]").value("liveness"))
                .andExpect(jsonPath("$.groups[1]").value("readiness"));
    }

    @Test
    void actuator_health_probes_answer_without_a_token() throws Exception {
        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void the_auth_counters_are_scrapeable_without_a_token() throws Exception {
        mockMvc.perform(get("/defendants/cases/20GD1234567"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/actuator/prometheus"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "auth_tokens_rejected_total")));
    }

    /** A near miss on an exempt path stays protected. */
    @Test
    void an_unexposed_actuator_endpoint_is_not_reachable_without_a_token() throws Exception {
        mockMvc.perform(get("/actuator/env"))
                .andExpect(status().isUnauthorized());
    }
}
