package ru.repairradar.service;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import ru.repairradar.config.ProgramApiProperties;
import ru.repairradar.dto.ProgramApiPage;
import ru.repairradar.dto.ProgramResponse;
import ru.repairradar.exception.ProgramImportException;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

@Component
@Validated
@RequiredArgsConstructor
public class ProgramApiClient {

    private final RestClient programRestClient;
    private final ProgramApiProperties properties;
    private final ObjectMapper objectMapper;

    @Valid
    public ProgramApiPage fetch(UUID houseGuid) {
        try {
            String body = programRestClient.get()
                    .uri(properties.url().toString() + "/" + houseGuid)
                    .retrieve().body(String.class);
            if (body == null || body.isBlank()) {
                throw new ProgramImportException("Вышестоящий сервис вернул пустой ответ");
            }
            ProgramResponse response = objectMapper.readValue(body, ProgramResponse.class);
            return new ProgramApiPage(response, body);
        } catch (RestClientResponseException e) {
            throw new ProgramImportException("Вышестоящий сервис вернул HTTP " + e.getStatusCode().value()
                    + "; тело ответа: " + e.getResponseBodyAsString(), e);
        }
    }
}