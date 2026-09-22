package ru.repairradar.dto;

import java.util.UUID;

public record GarObject(long objectId, UUID guid, String name, String typeName, int level) {

    public String label() {
        return typeName + " " + name;
    }
}
