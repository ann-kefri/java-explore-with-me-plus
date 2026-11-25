package ru.practicum.event.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import ru.practicum.event.model.AdminEventStateAction;

import java.time.LocalDateTime;

public record UpdateEventAdminRequest(
        @Size(min = 20, max = 2000)
        String annotation,

        Long category,

        @Size(min = 20, max = 7000)
        String description,

        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        LocalDateTime eventDate,

        @Valid
        LocationDto location,

        Boolean paid,

        @Min(0)
        Integer participantLimit,

        Boolean requestModeration,

        AdminEventStateAction stateAction,

        @Size(min = 3, max = 120)
        String title
) {
}
