package ru.practicum.event.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.event.dto.EventFullDto;
import ru.practicum.event.dto.UpdateEventAdminRequest;
import ru.practicum.event.mapper.EventMapper;
import ru.practicum.event.model.Event;
import ru.practicum.event.model.EventState;
import ru.practicum.event.repository.EventRepository;
import ru.practicum.exception.ConflictException;
import ru.practicum.exception.NotFoundException;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AdminEventService {

    private final EventRepository eventRepository;
    private final EventMapper eventMapper;
    private final EventUpdater eventUpdater;

    @Transactional
    public EventFullDto updateAdminEvent(
            Long eventId,
            UpdateEventAdminRequest dto
    ) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException(
                        "Event with id=" + eventId + " was not found"
                ));

        eventUpdater.apply(event, dto);

        if (dto.eventDate() != null) {
            if (dto.eventDate().isBefore(LocalDateTime.now().plusHours(1))) {
                throw new ConflictException(
                        "Event date must be at least one hour in the future"
                );
            }

            event.setEventDate(dto.eventDate());
        }

        if (dto.stateAction() != null) {
            switch (dto.stateAction()) {
                case PUBLISH_EVENT -> {
                    if (event.getState() != EventState.PENDING) {
                        throw new ConflictException(
                                "Only pending events can be published"
                        );
                    }

                    if (event.getEventDate().isBefore(
                            LocalDateTime.now().plusHours(1))) {
                        throw new ConflictException(
                                "Event must start at least one hour after publication"
                        );
                    }

                    event.setState(EventState.PUBLISHED);
                    event.setPublishedOn(LocalDateTime.now());
                }

                case REJECT_EVENT -> {
                    if (event.getState() == EventState.PUBLISHED) {
                        throw new ConflictException(
                                "Published events cannot be rejected"
                        );
                    }

                    event.setState(EventState.CANCELED);
                }
            }
        }

        Event savedEvent = eventRepository.save(event);

        return eventMapper.toFullDto(savedEvent, 0L, 0L);
    }
}
