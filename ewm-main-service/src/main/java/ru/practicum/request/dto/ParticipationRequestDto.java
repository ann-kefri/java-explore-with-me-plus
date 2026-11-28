package ru.practicum.request.dto;

import ru.practicum.request.model.RequestStatus;

import java.time.LocalDateTime;


public record ParticipationRequestDto(
        Long id,
        LocalDateTime created,
        Long event,
        Long requester,
        RequestStatus status) {

}
