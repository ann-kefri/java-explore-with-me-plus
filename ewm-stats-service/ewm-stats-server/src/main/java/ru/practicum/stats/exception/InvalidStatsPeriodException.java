package ru.practicum.stats.exception;

public class InvalidStatsPeriodException extends RuntimeException {

    private InvalidStatsPeriodException(String message) {
        super(message);
    }

    public static InvalidStatsPeriodException invalidRange() {
        return new InvalidStatsPeriodException(
                "Start must be before end"
        );
    }
}
