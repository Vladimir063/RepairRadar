package ru.repairradar.exception;

public class OperationInProgressException extends RuntimeException {

    public OperationInProgressException() {
        super("Address operation is already running");
    }
}
