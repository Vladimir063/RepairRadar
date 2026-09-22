package ru.repairradar.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;

@ConfigurationProperties(prefix = "repairradar.gar")
public record GarProperties(Path directory) {
}
