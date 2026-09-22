package ru.repairradar.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class AddressExceptionHandler {

    @ExceptionHandler(RepairOperationConflictException.class)
    public ProblemDetail repairBusy(RepairOperationConflictException exception) {
        return problem(HttpStatus.CONFLICT, "REPAIR_OPERATION_IN_PROGRESS", exception.getMessage());
    }

    @ExceptionHandler(RepairJobNotFoundException.class)
    public ProblemDetail repairJobNotFound(RepairJobNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "REPAIR_JOB_NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler(ProgramJobNotFoundException.class)
    public ProblemDetail programJobNotFound(ProgramJobNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "PROGRAM_JOB_NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail badSearchRequest(IllegalArgumentException exception) {
        log.warn("Запрос поиска программ отклонён: {}", exception.getMessage());
        return problem(HttpStatus.BAD_REQUEST, "PROGRAM_SEARCH_INVALID", exception.getMessage());
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ProblemDetail missingSearchParameter(MissingServletRequestParameterException exception) {
        log.warn("Параметр запроса поиска программ не передан: {}", exception.getParameterName());
        return problem(HttpStatus.BAD_REQUEST, "PROGRAM_SEARCH_INVALID",
                "Не передан обязательный параметр: " + exception.getParameterName());
    }

    @ExceptionHandler(GarImportException.class)
    public ProblemDetail source(GarImportException exception) {
        log.warn("GAR import failed: {}", exception.getMessage(), exception);
        return problem(HttpStatus.UNPROCESSABLE_CONTENT, exception.code(), "Address source could not be imported");
    }

    @ExceptionHandler(OperationInProgressException.class)
    public ProblemDetail busy(OperationInProgressException exception) {
        return problem(HttpStatus.CONFLICT, "ADDRESS_OPERATION_IN_PROGRESS", exception.getMessage());
    }

    @ExceptionHandler(DataAccessException.class)
    public ProblemDetail database(DataAccessException exception) {
        log.error("Address database operation failed", exception);
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "DATABASE_ERROR", "Database operation failed");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ProblemDetail notFound(NoResourceFoundException exception) {
        log.warn("Address API resource not found: {}", exception.getMessage());
        return problem(HttpStatus.NOT_FOUND, "ADDRESS_RESOURCE_NOT_FOUND", "Address resource not found");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ProblemDetail methodNotAllowed(HttpRequestMethodNotSupportedException exception) {
        log.warn("Address API method not allowed: {}", exception.getMessage());
        return problem(HttpStatus.METHOD_NOT_ALLOWED, "ADDRESS_METHOD_NOT_ALLOWED", "Address method not allowed");
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail unexpected(Exception exception) {
        log.error("Unexpected address operation failure", exception);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Unexpected server error");
    }

    private ProblemDetail problem(HttpStatus status, String code, String detail) {
        var problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle("RepairRadar address API error");
        problem.setProperty("code", code);
        return problem;
    }
}
