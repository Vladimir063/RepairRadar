package ru.repairradar.exception;

import java.util.UUID;

public class ProgramJobNotFoundException extends RuntimeException {

    public ProgramJobNotFoundException(UUID id) {
        super("Задача импорта программ не найдена: " + id);
    }
}