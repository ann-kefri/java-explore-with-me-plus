package ru.practicum.request.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import ru.practicum.request.model.RequestStatusAction;

import java.util.List;

public record EventRequestStatusUpdateRequest(
        @NotEmpty
        List<Long> requestIds,
        @NotNull
        RequestStatusAction status
) {}
