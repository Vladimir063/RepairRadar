package ru.repairradar;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class RepairRadarApplication {

    public static void main(String[] args) {
        SpringApplication.run(RepairRadarApplication.class, args);
    }
}
