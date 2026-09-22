package ru.repairradar.dto;

import java.util.UUID;

public record GarHouse(
        long objectId,
        UUID guid,
        String number,
        Integer type,
        String additionalNumber1,
        Integer additionalType1,
        String additionalNumber2,
        Integer additionalType2) {
}
