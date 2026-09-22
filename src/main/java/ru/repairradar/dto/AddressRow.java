package ru.repairradar.dto;

import java.util.UUID;

public record AddressRow(
        String city,
        String fullAddress,
        String street,
        String houseNumber,
        String additionalNumber1,
        Integer additionalType1,
        String additionalNumber2,
        Integer additionalType2,
        Integer houseType,
        long streetObjectId,
        UUID streetGuid,
        long houseObjectId,
        UUID houseGuid) {
}
