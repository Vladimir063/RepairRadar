package ru.repairradar.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

@Configuration
public class ProgramImportConfig {

    @Bean
    public RestClient programRestClient(ProgramApiProperties properties) {
        var httpClient = HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build();
        var factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(properties.readTimeout());
        return RestClient.builder().requestFactory(factory)
                .defaultHeader(HttpHeaders.USER_AGENT, "PostmanRuntime/7.51.0")
                .build();
    }
}