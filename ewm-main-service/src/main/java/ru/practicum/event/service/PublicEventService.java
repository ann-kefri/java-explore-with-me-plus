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
import ru.practicum.event.model.EventSort;
import ru.practicum.event.model.EventState;
import ru.practicum.event.repository.EventRepository;
import ru.practicum.event.repository.EventSpecifications;
import ru.practicum.exception.NotFoundException;
import ru.practicum.stats.client.StatsClient;
import ru.practicum.stats.dto.EndpointHit;
import ru.practicum.stats.dto.ViewStats;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PublicEventService {

    private final EventRepository eventRepository;
    private final EventMapper eventMapper;
    private final StatsClient statsClient;

    @Transactional(readOnly = true)
    public EventFullDto getEvent(Long eventId, String ip) {
        Event event = eventRepository.findById(eventId)
                .filter(e -> e.getState() == EventState.PUBLISHED)
                .orElseThrow(() -> new NotFoundException(
                        "Published event with id=" + eventId + " was not found"
                ));

        statsClient.hit(EndpointHit.builder()
                .app("ewm-main-service")
                .uri("/events/" + eventId)
                .ip(ip)
                .timestamp(LocalDateTime.now())
                .build());

        List<ViewStats> stats = statsClient.getStats(
                event.getPublishedOn(),
                LocalDateTime.now(),
                List.of("/events/" + eventId),
                true
        );

        long views = stats.stream()
                .mapToLong(ViewStats::getHits)
                .sum();

        return eventMapper.toFullDto(event, 0L, views);
    }

    @Transactional(readOnly = true)
    public List<EventShortDto> getEvents(
            String text,
            List<Long> categories,
            Boolean paid,
            LocalDateTime rangeStart,
            LocalDateTime rangeEnd,
            int from,
            int size,
            EventSort sort
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

        Specification<Event> spec = Specification
                .where(EventSpecifications.published())
                .and(EventSpecifications.byText(text))
                .and(EventSpecifications.byCategories(categories))
                .and(EventSpecifications.byPaid(paid))
                .and(EventSpecifications.after(rangeStart))
                .and(EventSpecifications.before(rangeEnd));

        List<Event> events;

        if (sort == EventSort.VIEWS) {
            // Для сортировки по просмотрам сначала получаем все
            // подходящие события, без пагинации.
            events = eventRepository.findAll(spec);

            Map<String, Long> viewsByUri = getViews(events);

            // Сортируем по убыванию просмотров,
            // затем применяем пагинацию.
            events = events.stream()
                    .sorted(
                            Comparator
                                    .comparingLong((Event event) ->
                                            viewsByUri.getOrDefault(
                                                    "/events/" + event.getId(),
                                                    0L
                                            )
                                    )
                                    .reversed()
                                    .thenComparing(Event::getId)
                    )
                    .skip(from)
                    .limit(size)
                    .toList();

            return events.stream()
                    .map(event -> eventMapper.toShortDto(
                            event,
                            0L,
                            viewsByUri.getOrDefault(
                                    "/events/" + event.getId(),
                                    0L
                            )
                    ))
                    .toList();
        }

        // EVENT_DATE: сортировку и пагинацию выполняет PostgreSQL.
        Sort sorting = Sort.by(
                Sort.Order.asc("eventDate"),
                Sort.Order.asc("id")
        );

        Pageable pageable = new OffsetPageRequest(from, size, sorting);

        events = eventRepository.findAll(spec, pageable)
                .getContent();

        Map<String, Long> viewsByUri = getViews(events);

        return events.stream()
                .map(event -> eventMapper.toShortDto(
                        event,
                        0L,
                        viewsByUri.getOrDefault(
                                "/events/" + event.getId(),
                                0L
                        )
                ))
                .toList();
    }

    private Map<String, Long> getViews(List<Event> events) {
        if (events.isEmpty()) {
            return Map.of();
        }

        List<String> uris = events.stream()
                .map(event -> "/events/" + event.getId())
                .toList();

        LocalDateTime start = events.stream()
                .map(Event::getPublishedOn)
                .min(LocalDateTime::compareTo)
                .orElseThrow();

        return statsClient.getStats(
                        start,
                        LocalDateTime.now(),
                        uris,
                        true
                ).stream()
                .collect(Collectors.toMap(
                        ViewStats::getUri,
                        ViewStats::getHits,
                        Long::sum
                ));
    }
}
