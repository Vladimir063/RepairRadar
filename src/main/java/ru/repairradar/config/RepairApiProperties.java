package ru.repairradar.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.net.URI;
import java.time.Duration;

@ConfigurationProperties("repairradar.repair-api")
public record RepairApiProperties(
        @DefaultValue("https://dom.gosuslugi.ru/capital-repair-programs/api/rest/services/programs/public/house-repair-search") URI url,
        @DefaultValue("10s") Duration connectTimeout,
        @DefaultValue("60s") Duration readTimeout,
        @DefaultValue("10000") int maxPages) {

    public RepairApiProperties {
        if (maxPages < 1 || connectTimeout.isNegative() || connectTimeout.isZero()
                || readTimeout.isNegative() || readTimeout.isZero()) {
            throw new IllegalArgumentException("Repair API timeouts and maxPages must be positive");
        }
    }
}
