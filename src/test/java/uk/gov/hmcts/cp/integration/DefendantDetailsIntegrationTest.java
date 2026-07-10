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
