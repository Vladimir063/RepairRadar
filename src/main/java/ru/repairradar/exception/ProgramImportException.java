package ru.repairradar.exception;

public class ProgramImportException extends RuntimeException {

    public ProgramImportException(String message) {
        super(message);
    }

    public ProgramImportException(String message, Throwable cause) {
        super(message, cause);
    }
}