package ru.repairradar.exception;

public class RepairImportException extends RuntimeException {

    public RepairImportException(String message) {
        super(message);
    }

    public RepairImportException(String message, Throwable cause) {
        super(message, cause);
    }
}
