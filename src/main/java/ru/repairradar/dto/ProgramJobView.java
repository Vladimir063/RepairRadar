package ru.repairradar.dto;

import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

public record ProgramJobView(UUID id, RepairJobStatus status, Instant queuedAt, Instant startedAt,
                             Instant finishedAt, JsonNode selectedHouseGuids, int successfulHouses,
                             int failedHouses, int worksSaved, String errorDetails) {
}