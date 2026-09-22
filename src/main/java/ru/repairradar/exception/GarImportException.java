package ru.repairradar.exception;

public class GarImportException extends RuntimeException {

    private final String code;

    public GarImportException(String code, String message) {
        this(code, message, null);
    }

    public GarImportException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
