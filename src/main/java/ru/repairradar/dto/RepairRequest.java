package ru.repairradar.dto;

import java.util.UUID;

public record RepairRequest(int pageIndex, int itemsPerPage, FiasAddress fiasAddress) {

    private static final UUID MOSCOW_GUID = UUID.fromString("0c5b2444-70a0-4932-980c-b4dc0d3f02b5");

    public static RepairRequest forStreet(UUID streetGuid, int pageIndex) {
        return new RepairRequest(pageIndex, 100, new FiasAddress(MOSCOW_GUID, streetGuid));
    }

    public record FiasAddress(UUID regionGuid, UUID streetGuid) {
    }
}
