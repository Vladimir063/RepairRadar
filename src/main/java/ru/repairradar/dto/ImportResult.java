package ru.repairradar.dto;

public record ImportResult(int requested, int imported, long total, boolean exhausted) {
}
