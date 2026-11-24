package ru.practicum.event.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.common.OffsetPageRequest;
import ru.practicum.event.dto.EventFullDto;
import ru.practicum.event.dto.UpdateEventAdminRequest;
import ru.practicum.event.mapper.EventMapper;
import ru.practicum.event.model.Event;
import ru.practicum.event.model.EventState;
import ru.practicum.event.repository.EventRepository;
import ru.practicum.event.repository.EventSpecifications;
import ru.practicum.exception.ConflictException;
import ru.practicum.exception.NotFoundException;

import java.time.LocalDateTime;
import java.util.List;

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

    @Transactional(readOnly = true)
    public List<EventFullDto> getAdminEvents(
            List<Long> users,
            List<EventState> states,
            List<Long> categories,
            LocalDateTime rangeStart,
            LocalDateTime rangeEnd,
            int from,
            int size
    ) {
        if (from < 0 || size < 1) {
            throw new IllegalArgumentException(
                    "Pagination parameters must satisfy from >= 0 and size > 0"
            );
        }

        if (rangeStart != null && rangeEnd != null
            && rangeStart.isAfter(rangeEnd)) {
            throw new IllegalArgumentException(
                    "Range start must not be after range end"
            );
        }

        Specification<Event> specification = Specification
                .where(EventSpecifications.byUsers(users))
                .and(EventSpecifications.byStates(states))
                .and(EventSpecifications.byCategories(categories))
                .and(EventSpecifications.after(rangeStart))
                .and(EventSpecifications.before(rangeEnd));

        Pageable pageable = new OffsetPageRequest(
                from,
                size,
                Sort.by(Sort.Direction.ASC, "id")
        );

        return eventRepository.findAll(specification, pageable)
                .getContent()
                .stream()
                .map(event -> eventMapper.toFullDto(event, 0L, 0L))
                .toList();
    }
}
