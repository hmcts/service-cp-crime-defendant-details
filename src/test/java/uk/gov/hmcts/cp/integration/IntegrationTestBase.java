package uk.gov.hmcts.cp.integration;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import uk.gov.hmcts.cp.auth.TestTokens;
import uk.gov.hmcts.cp.config.AppPropertiesBackend;

@SpringBootTest(properties = {
    "auth.mode=ENFORCE",
    "auth.tenant-id=" + TestTokens.TENANT_ID,
    "auth.audience=" + TestTokens.AUDIENCE,
    "auth.required-role=" + TestTokens.REQUIRED_ROLE,
    "auth.clock-skew-seconds=60"})
@AutoConfigureMockMvc
@Import({TestAuthConfiguration.class, AuthenticatedMockMvcConfiguration.class})
@Slf4j
public abstract class IntegrationTestBase {

    @Autowired
    AppPropertiesBackend appProperties;

    @Resource
    protected MockMvc mockMvc;
}