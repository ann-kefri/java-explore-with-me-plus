package ru.practicum.exception;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
        List<String> errors,
        String status,
        String reason,
        String message,

        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        LocalDateTime timestamp
) {
    public static ApiError of(
            HttpStatus status,
            String reason,
            String message
    ) {
        return new ApiError(
                null,
                status.name(),
                reason,
                message,
                LocalDateTime.now()
        );
    }
}
