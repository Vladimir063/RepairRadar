package ru.repairradar.service;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import ru.repairradar.config.RepairApiProperties;
import ru.repairradar.dto.*;
import ru.repairradar.exception.RepairImportException;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

@Component
@Validated
@RequiredArgsConstructor
public class RepairApiClient {

    private final RestClient repairRestClient;
    private final RepairApiProperties properties;
    private final ObjectMapper objectMapper;

    @Valid
    public RepairApiPage fetch(UUID streetGuid, int pageIndex) {
        try {
            String body = repairRestClient.post().uri(properties.url())
                    .body(RepairRequest.forStreet(streetGuid, pageIndex)).retrieve().body(String.class);
            if (body == null || body.isBlank()) {
                throw new RepairImportException("Upstream returned an empty body");
            }
            RepairResponse response = objectMapper.readValue(body, RepairResponse.class);
            return new RepairApiPage(response, body);
        } catch (RestClientResponseException e) {
            throw new RepairImportException("Upstream HTTP " + e.getStatusCode().value()
                    + "; response body: " + e.getResponseBodyAsString(), e);
        }
    }
}
