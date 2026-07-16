package uk.gov.hmcts.cp.integration;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static java.net.HttpURLConnection.HTTP_OK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static uk.gov.hmcts.cp.filters.http.TracingFilter.CORRELATION_ID_KEY;

class TracingIntegrationTest extends IntegrationTestBase {

    private static final String TEST_CORRELATION_ID = "12345678-1234-1234-1234-123456789012";

    private final String caseUrn = "20GD1234567";
    private final UUID caseId = UUID.randomUUID();

    private WireMockServer wireMockServer;

    @BeforeEach
    void beforeEach() {
        wireMockServer = new WireMockServer(WireMockConfiguration.options().port(8081));
        wireMockServer.start();
        WireMock.configureFor("localhost", 8081);
        stubDownstreamResponses();
    }

    @AfterEach
    void afterEach() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }

    @Test
    void request_with_correlation_id_header_should_echo_it_in_response() throws Exception {
        MvcResult result = mockMvc.perform(get("/defendants/cases/{case_urn}", caseUrn)
                        .accept(MediaType.APPLICATION_JSON)
                        .header(CORRELATION_ID_KEY, TEST_CORRELATION_ID))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getResponse().getHeader(CORRELATION_ID_KEY)).isEqualTo(TEST_CORRELATION_ID);
    }

    @Test
    void request_without_correlation_id_header_should_generate_one_in_response() throws Exception {
        MvcResult result = mockMvc.perform(get("/defendants/cases/{case_urn}", caseUrn)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getResponse().getHeader(CORRELATION_ID_KEY)).isNotBlank();
    }

    private void stubDownstreamResponses() {
        String mappingUrl = String.format("%s/%s", appProperties.getCaseMapperPath(), caseUrn);
        String mappingResponseBody = String.format("{\"caseUrn\":\"%s\", \"caseId\":\"%s\"}", caseUrn, caseId);
        stubFor(WireMock.get(urlEqualTo(mappingUrl)).willReturn(aResponse()
                .withStatus(HTTP_OK)
                .withHeader("Content-Type", "application/json")
                .withBody(mappingResponseBody)));

        String progressionUrl = String.format("%s/%s", appProperties.getProgressionPath(), caseId);
        stubFor(WireMock.get(urlEqualTo(progressionUrl)).willReturn(aResponse()
                .withStatus(HTTP_OK)
                .withHeader("Content-Type", "application/json")
                .withBody(readResourceContents("cp_response.json"))));
    }

    @SneakyThrows
    private String readResourceContents(final String resourceName) {
        URL resource = getClass().getClassLoader().getResource(resourceName);
        return Files.readString(Path.of(resource.toURI()));
    }
}