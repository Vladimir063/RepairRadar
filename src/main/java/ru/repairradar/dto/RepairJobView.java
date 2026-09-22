package ru.repairradar.dto;

import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

public record RepairJobView(UUID id, RepairJobStatus status, Instant queuedAt, Instant startedAt,
                            Instant finishedAt, JsonNode selectedStreetGuids, int successfulStreets,
                            int failedStreets, int pagesSaved, long itemsSaved, String errorDetails) {
}
