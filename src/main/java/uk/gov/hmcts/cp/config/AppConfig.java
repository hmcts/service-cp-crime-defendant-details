package uk.gov.hmcts.cp.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.net.http.HttpClient;

@Configuration
public class AppConfig {

    @Value("${spring.application.name:service-cp-crime-defendant-details}")
    private String applicationName;

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    @Bean
    public HttpClient httpClient() {
        return HttpClient.newHttpClient();
    }
}