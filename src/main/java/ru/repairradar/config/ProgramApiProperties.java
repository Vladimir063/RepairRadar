package ru.repairradar.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.net.URI;
import java.time.Duration;

@ConfigurationProperties("repairradar.program-api")
public record ProgramApiProperties(
        @DefaultValue("https://dom.gosuslugi.ru/capital-repair-programs/api/rest/services/programs/public/objects/rpkr") URI url,
        @DefaultValue("10s") Duration connectTimeout,
        @DefaultValue("60s") Duration readTimeout) {

    public ProgramApiProperties {
        if (connectTimeout.isNegative() || connectTimeout.isZero()
                || readTimeout.isNegative() || readTimeout.isZero()) {
            throw new IllegalArgumentException("Program API timeouts must be positive");
        }
    }
}