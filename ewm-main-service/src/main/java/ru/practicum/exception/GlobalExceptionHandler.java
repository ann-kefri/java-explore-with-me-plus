package ru.practicum.exception;

import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler({
            IllegalArgumentException.class,
            ConstraintViolationException.class,
            MethodArgumentNotValidException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class
    })
    public ResponseEntity<ApiError> handleBadRequest(Exception ex) {
        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "Incorrectly made request.",
                ex.getMessage()
        );
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(NotFoundException ex) {
        return buildResponse(
                HttpStatus.NOT_FOUND,
                "The required object was not found.",
                ex.getMessage()
        );
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiError> handleConflict(ConflictException ex) {
        return buildResponse(
                HttpStatus.CONFLICT,
                "For the requested operation the conditions are not met.",
                ex.getMessage()
        );
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleIntegrity(DataIntegrityViolationException ex) {
        return buildResponse(
                HttpStatus.CONFLICT,
                "Integrity constraint has been violated.",
                ex.getMostSpecificCause().getMessage()
        );
    }

    private ResponseEntity<ApiError> buildResponse(
            HttpStatus status,
            String reason,
            String message
    ) {
        return ResponseEntity
                .status(status)
                .body(ApiError.of(status, reason, message));
    }
}
