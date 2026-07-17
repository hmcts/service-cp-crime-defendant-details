package uk.gov.hmcts.cp.integration;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static java.net.HttpURLConnection.HTTP_NOT_FOUND;
import static java.net.HttpURLConnection.HTTP_OK;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Slf4j
class DefendantDetailsIntegrationTest extends IntegrationTestBase {

    String caseUrn = "20GD1234567";
    UUID caseId = UUID.randomUUID();

    // case 28DI4469859 — single defendant, masterDefendantId == own id
    private static final String CASE_URN_PARTIAL_MATCH = "28DI4469859";
    private static final UUID CASE_ID_PARTIAL_MATCH = UUID.fromString("2f354689-fdda-472d-82a1-7b2ebdb0ac48");

    // case 28DI1264111 — two defendants, each with distinct masterDefendantId
    private static final String CASE_URN_DISTINCT_MASTER_IDS = "28DI1264111";
    private static final UUID CASE_ID_DISTINCT_MASTER_IDS = UUID.fromString("2ad1f637-25c9-4ffe-a60d-a02468345ffd");

    // case 28DI3988847 — two defendants sharing the same masterDefendantId
    private static final String CASE_URN_SHARED_MASTER_ID = "28DI3988847";
    private static final UUID CASE_ID_SHARED_MASTER_ID = UUID.fromString("68024d20-2c3f-4d5e-84d6-dba4faeabc61");

    // masterDefendantId shared by both defendants on case 28DI3988847
    private static final UUID SHARED_MASTER_DEF_ID = UUID.fromString("f6d5d01b-02f1-453d-a528-68e418a6478b");

    // UUID not present in any fixture — filtering by this returns empty
    private static final UUID UNRELATED_MASTER_DEF_ID = UUID.fromString("eeee0001-0000-0000-0000-000000000001");

    // Kennedy Becker's defendant ID on case 28DI3988847 — shares SHARED_MASTER_DEF_ID with Tommie Becker
    private static final UUID DEF_ID_WITH_SHARED_MASTER_DEF_ID = UUID.fromString("21b18f18-f8f2-40f2-a116-8d0d86702664");

    protected WireMockServer wireMockServer;

    @BeforeEach
    void beforeEach() {
        wireMockServer = new WireMockServer(WireMockConfiguration.options().port(8081));
        wireMockServer.start();
        WireMock.configureFor("localhost", 8081);
    }

    @AfterEach
    void afterEach() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }

    @Test
    void get_defendant_details_for_progression_response_should_return_ok() {
        String cpResponse = "cp_response.json";
        String expectedDefendantDetailsResponse = "expected_defendant_details_response.json";

        stub_cp_response_and_verify_expected_defendant_details_response(cpResponse, expectedDefendantDetailsResponse);
    }

    @Test
    void bad_caseurn_should_return_404() throws Exception {
        String expectedUrl = String.format("%s/%s", appProperties.getCaseMapperPath(), caseUrn);
        ResponseDefinitionBuilder mockResponse = aResponse()
                .withStatus(HTTP_NOT_FOUND)
                .withHeader("Content-Type", "application/json");
        log.info("Stubbing mapping url:{}", expectedUrl);
        stubFor(WireMock.get(urlEqualTo(expectedUrl)).willReturn(mockResponse));

        mockMvc.perform(get("/defendants/cases/{case_urn}", caseUrn)
                        .accept(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isNotFound());
    }

    @Test
    void bad_progression_response_should_return_404() throws Exception {
        stubMappingResponse(caseUrn, caseId);
        String expectedProgressionUrl = String.format("%s%s/%s", appProperties.getProgressionUrl(), appProperties.getProgressionPath(), caseId);

        ResponseDefinitionBuilder mockResponse = aResponse()
                .withStatus(HTTP_NOT_FOUND)
                .withHeader("Content-Type", "application/json");
        log.info("Stubbing progression response url:{}", expectedProgressionUrl);
        stubFor(WireMock.get(urlEqualTo(expectedProgressionUrl)).willReturn(mockResponse));

        mockMvc.perform(get("/defendants/cases/{case_urn}", caseUrn)
                        .accept(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isNotFound());
    }

    @Test
    void empty_cp_response_should_return_404() throws Exception {
        stubMappingResponse(caseUrn, caseId);
        String expectedProgressionUrl = String.format("%s%s/%s", appProperties.getProgressionUrl(), appProperties.getProgressionPath(), caseId);

        ResponseDefinitionBuilder mockResponse = aResponse()
                .withStatus(HTTP_OK)
                .withHeader("Content-Type", "application/json")
                .withBody(readFileContents("cp_empty_response.json"));
        log.info("Stubbing progression response url:{}", expectedProgressionUrl);
        stubFor(WireMock.get(urlEqualTo(expectedProgressionUrl)).willReturn(mockResponse));

        mockMvc.perform(get("/defendants/cases/{case_urn}", caseUrn)
                        .accept(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isNotFound());
    }

    @Test
    void missing_path_variable_should_return_404() throws Exception {
        stubMappingResponse(caseUrn, caseId);
        String expectedProgressionUrlWithMissingCaseId = String.format("%s%s", appProperties.getProgressionUrl(), appProperties.getProgressionPath());

        ResponseDefinitionBuilder mockResponse = aResponse()
                .withStatus(HTTP_OK)
                .withHeader("Content-Type", "application/json")
                .withBody(readFileContents("cp_response.json"));

        log.info("Stubbing progression response url:{}", expectedProgressionUrlWithMissingCaseId);
        stubFor(WireMock.get(urlEqualTo(expectedProgressionUrlWithMissingCaseId)).willReturn(mockResponse));

        mockMvc.perform(get("/defendants/cases/{case_urn}", caseUrn)
                        .accept(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isNotFound());
    }

    @Test
    @SneakyThrows
    void defendants_with_distinct_masterDefendantIds_all_returned() {
        getDefendants("cp_defendants_before_cross_case_link.json", CASE_URN_DISTINCT_MASTER_IDS, CASE_ID_DISTINCT_MASTER_IDS)
                .andExpect(status().isOk())
                .andExpect(content().string(readFileContents("expected_defendants_distinct_master_ids.json")));
    }

    @Test
    @SneakyThrows
    void unmatched_defendant_returns_empty_when_filtered_by_unrelated_masterDefendantId() {
        getDefendantsByMaster("cp_defendant_partial_match.json", UNRELATED_MASTER_DEF_ID, CASE_URN_PARTIAL_MATCH, CASE_ID_PARTIAL_MATCH)
                .andExpect(status().isOk())
                .andExpect(content().string("[]"));
    }

    @Test
    @SneakyThrows
    void unmatched_defendant_has_own_id_as_masterDefendantId() {
        getDefendants("cp_defendant_partial_match.json", CASE_URN_PARTIAL_MATCH, CASE_ID_PARTIAL_MATCH)
                .andExpect(status().isOk())
                .andExpect(content().string(readFileContents("expected_defendant_partial_match.json")));
    }

    @Test
    @SneakyThrows
    void masterDefendantId_filter_returns_all_matching_defendants() {
        getDefendantsByMaster("cp_defendants_cross_case_matched.json", SHARED_MASTER_DEF_ID, CASE_URN_SHARED_MASTER_ID, CASE_ID_SHARED_MASTER_ID)
                .andExpect(status().isOk())
                .andExpect(content().string(readFileContents("expected_defendants_shared_master_id.json")));
    }

    @Test
    @SneakyThrows
    void defendantId_filter_returns_single_match_when_masterDefendantId_is_shared() {
        getDefendantsById("cp_defendants_cross_case_matched.json", DEF_ID_WITH_SHARED_MASTER_DEF_ID, CASE_URN_SHARED_MASTER_ID, CASE_ID_SHARED_MASTER_ID)
                .andExpect(status().isOk())
                .andExpect(content().string(readFileContents("expected_defendant_defendantId_filter.json")));
    }

    @SneakyThrows
    private ResultActions getDefendants(String fixture, String caseUrn, UUID caseId) {
        stubMappingResponse(caseUrn, caseId);
        stubGetProgressionCaseResponse(caseId, fixture);
        return mockMvc.perform(get("/defendants/cases/{caseUrn}", caseUrn)
                        .accept(MediaType.APPLICATION_JSON))
                .andDo(print());
    }

    @SneakyThrows
    private ResultActions getDefendantsByMaster(String fixture, UUID masterDefendantId, String caseUrn, UUID caseId) {
        stubMappingResponse(caseUrn, caseId);
        stubGetProgressionCaseResponse(caseId, fixture);
        return mockMvc.perform(get("/defendants/cases/{caseUrn}", caseUrn)
                        .param("masterDefendantId", masterDefendantId.toString())
                        .accept(MediaType.APPLICATION_JSON))
                .andDo(print());
    }

    @SneakyThrows
    private ResultActions getDefendantsById(String fixture, UUID defendantId, String caseUrn, UUID caseId) {
        stubMappingResponse(caseUrn, caseId);
        stubGetProgressionCaseResponse(caseId, fixture);
        return mockMvc.perform(get("/defendants/cases/{caseUrn}", caseUrn)
                        .param("defendantId", defendantId.toString())
                        .accept(MediaType.APPLICATION_JSON))
                .andDo(print());
    }

    private void stub_cp_response_and_verify_expected_defendant_details_response(String cpResponseFile, String expectedDefendantDetailsResponseFile) {
        stubMappingResponse(caseUrn, caseId);
        stubGetProgressionCaseResponse(caseId, cpResponseFile);

        String expectedResponse = readFileContents(expectedDefendantDetailsResponseFile);
        defendants_endpoint_and_verify_response(expectedResponse);
    }

    @SneakyThrows
    private void defendants_endpoint_and_verify_response(String expectedResponse) {
        mockMvc.perform(get("/defendants/cases/{case_urn}", caseUrn)
                        .accept(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(content().string(expectedResponse))
                .andReturn();
    }

    private void stubMappingResponse(String caseUrn, UUID caseId) {
        String expectedUrl = String.format("%s/%s", appProperties.getCaseMapperPath(), caseUrn);
        String responseBody = String.format("{\"caseUrn\":\"%s\", \"caseId\":\"%s\"}", caseUrn, caseId);
        ResponseDefinitionBuilder mockResponse = aResponse()
                .withStatus(HTTP_OK)
                .withHeader("Content-Type", "application/json")
                .withBody(responseBody);
        log.info("Stubbing mapping url:{}", expectedUrl);
        stubFor(WireMock.get(urlEqualTo(expectedUrl)).willReturn(mockResponse));
    }

    private void stubGetProgressionCaseResponse(UUID caseId, String filename) {
        String expectedUrl = String.format("%s/%s", appProperties.getProgressionPath(), caseId);
        ResponseDefinitionBuilder mockResponse = aResponse()
                .withStatus(HTTP_OK)
                .withHeader("Content-Type", "application/json")
                .withBody(readFileContents(filename));
        log.info("Stubbing progression url:{}", expectedUrl);
        stubFor(WireMock.get(urlEqualTo(expectedUrl)).willReturn(mockResponse));
    }

    @SneakyThrows
    private String readFileContents(final String resourceName) {
        URL resource = getClass().getClassLoader().getResource(resourceName);
        return Files.readString(Path.of(resource.toURI()));
    }
}
