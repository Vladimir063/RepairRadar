package ru.repairradar.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record ProgramApiPage(@Valid @NotNull ProgramResponse response, String body) {
}