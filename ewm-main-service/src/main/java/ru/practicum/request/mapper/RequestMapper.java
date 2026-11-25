package ru.practicum.request.mapper;

import org.springframework.stereotype.Component;
import ru.practicum.request.dto.ParticipationRequestDto;
import ru.practicum.request.model.ParticipationRequest;

@Component
public class RequestMapper {
    public ParticipationRequestDto toDto(ParticipationRequest request) {
        return new ParticipationRequestDto(
                request.getId(), request.getCreated(), request.getEvent().getId(), request.getRequester().getId(), request.getStatus()
        );
    }
}
