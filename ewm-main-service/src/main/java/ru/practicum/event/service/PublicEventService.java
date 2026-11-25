package ru.practicum.event.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.common.OffsetPageRequest;
import ru.practicum.event.dto.EventFullDto;
import ru.practicum.event.dto.EventShortDto;
import ru.practicum.event.mapper.EventMapper;
import ru.practicum.event.model.Event;
import ru.practicum.event.model.EventState;
import ru.practicum.event.repository.EventRepository;
import ru.practicum.event.repository.EventSpecifications;
import ru.practicum.exception.NotFoundException;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PublicEventService {

    private final EventRepository eventRepository;
    private final EventMapper eventMapper;

    @Transactional(readOnly = true)
    public EventFullDto getEvent(Long eventId) {
        Event event = eventRepository.findById(eventId)
                .filter(e -> e.getState() == EventState.PUBLISHED)
                .orElseThrow(() -> new NotFoundException(
                        "Published event with id=" + eventId + " was not found"
                ));

        return eventMapper.toFullDto(event, 0L, 0L);
    }

    @Transactional(readOnly = true)
    public List<EventShortDto> getEvents(
            String text,
            List<Long> categories,
            Boolean paid,
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
                .where(EventSpecifications.published())
                .and(EventSpecifications.byText(text))
                .and(EventSpecifications.byCategories(categories))
                .and(EventSpecifications.byPaid(paid))
                .and(EventSpecifications.after(rangeStart))
                .and(EventSpecifications.before(rangeEnd));

        Pageable pageable = new OffsetPageRequest(
                from,
                size,
                Sort.by(Sort.Direction.ASC, "eventDate")
                        .and(Sort.by(Sort.Direction.ASC, "id"))
        );

        return eventRepository.findAll(specification, pageable)
                .getContent()
                .stream()
                .map(event -> eventMapper.toShortDto(event, 0L, 0L))
                .toList();
    }
}
