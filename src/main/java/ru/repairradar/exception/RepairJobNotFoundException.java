package ru.repairradar.exception;

import java.util.UUID;

public class RepairJobNotFoundException extends RuntimeException {

    public RepairJobNotFoundException(UUID id) {
        super("Repair import job not found: " + id);
    }
}
