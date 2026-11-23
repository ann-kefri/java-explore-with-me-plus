package ru.practicum.stats.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class StatsExceptionHandler {

    @ExceptionHandler(InvalidStatsPeriodException.class)
    public ResponseEntity<ProblemDetail> handleInvalidPeriod(
            InvalidStatsPeriodException exception) {

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                exception.getMessage()
        );

        return ResponseEntity.badRequest().body(problem);
    }
}
