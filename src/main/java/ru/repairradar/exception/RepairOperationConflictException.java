package ru.repairradar.exception;

public class RepairOperationConflictException extends RuntimeException {

    public RepairOperationConflictException() {
        super("A repair import or clear operation is already running");
    }
}
