package ru.repairradar.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record RepairApiPage(@Valid @NotNull RepairResponse response, String body) {
}