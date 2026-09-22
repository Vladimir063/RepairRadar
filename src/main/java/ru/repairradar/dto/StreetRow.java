package ru.repairradar.dto;

import java.util.UUID;

public record StreetRow(UUID streetGuid, long streetObjectId, String name, String city,
                        String fullAddress, String locality, String hierarchyPath) {
}
