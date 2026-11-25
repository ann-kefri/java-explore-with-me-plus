package ru.practicum.event.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import ru.practicum.event.dto.EventFullDto;
import ru.practicum.event.dto.EventShortDto;
import ru.practicum.event.mapper.EventMapper;
import ru.practicum.event.model.Event;
import ru.practicum.event.model.EventSort;
import ru.practicum.event.model.EventState;
import ru.practicum.event.repository.EventRepository;
import ru.practicum.exception.NotFoundException;
import ru.practicum.stats.client.StatsClient;
import ru.practicum.stats.dto.ViewStats;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublicEventServiceTest {

    private static final String TEST_IP = "127.0.0.1";

    private static final LocalDateTime PUBLISHED_ON =
            LocalDateTime.of(2026, 11, 24, 21, 0);

    @Mock
    private EventRepository eventRepository;

    @Mock
    private EventMapper eventMapper;

    @Mock
    private StatsClient statsClient;

    @InjectMocks
    private PublicEventService eventService;

    @Test
    void getEvent_shouldReturnPublishedEvent() {
        Event event = createPublishedEvent();

        EventFullDto dto = mock(EventFullDto.class);

        when(eventRepository.findById(1L))
                .thenReturn(Optional.of(event));

        when(statsClient.getStats(
                eq(PUBLISHED_ON),
                any(LocalDateTime.class),
                eq(List.of("/events/1")),
                eq(true)
        )).thenReturn(List.of());

        when(eventMapper.toFullDto(event, 0L, 0L))
                .thenReturn(dto);

        EventFullDto result = eventService.getEvent(1L, TEST_IP);

        assertSame(dto, result);
        verify(eventRepository).findById(1L);

        verify(statsClient).hit(argThat(hit ->
                "ewm-main-service".equals(hit.getApp())
                && "/events/1".equals(hit.getUri())
                && TEST_IP.equals(hit.getIp())
                && hit.getTimestamp() != null
        ));

        verify(statsClient).getStats(
                eq(PUBLISHED_ON),
                any(LocalDateTime.class),
                eq(List.of("/events/1")),
                eq(true)
        );
    }

    @Test
    void getEvent_shouldReturnActualViews() {
        Event event = createPublishedEvent();

        ViewStats stats = new ViewStats();
        stats.setApp("ewm-main-service");
        stats.setUri("/events/1");
        stats.setHits(5L);

        EventFullDto dto = mock(EventFullDto.class);

        when(eventRepository.findById(1L))
                .thenReturn(Optional.of(event));

        when(statsClient.getStats(
                eq(PUBLISHED_ON),
                any(LocalDateTime.class),
                eq(List.of("/events/1")),
                eq(true)
        )).thenReturn(List.of(stats));

        when(eventMapper.toFullDto(event, 0L, 5L))
                .thenReturn(dto);

        EventFullDto result = eventService.getEvent(1L, TEST_IP);

        assertSame(dto, result);

        verify(statsClient).hit(any());
        verify(eventMapper).toFullDto(event, 0L, 5L);
    }

    @Test
    void getEvent_shouldNotReturnPendingEvent() {
        Event event = new Event();
        event.setId(2L);
        event.setState(EventState.PENDING);

        when(eventRepository.findById(2L))
                .thenReturn(Optional.of(event));

        assertThrows(
                NotFoundException.class,
                () -> eventService.getEvent(2L, TEST_IP)
        );

        verifyNoInteractions(eventMapper, statsClient);
    }

    @Test
    void getEvent_shouldNotReturnCanceledEvent() {
        Event event = new Event();
        event.setId(3L);
        event.setState(EventState.CANCELED);

        when(eventRepository.findById(3L))
                .thenReturn(Optional.of(event));

        assertThrows(
                NotFoundException.class,
                () -> eventService.getEvent(3L, TEST_IP)
        );

        verifyNoInteractions(eventMapper, statsClient);
    }

    @Test
    void getEvent_shouldReturnNotFoundForUnknownId() {
        when(eventRepository.findById(99L))
                .thenReturn(Optional.empty());

        assertThrows(
                NotFoundException.class,
                () -> eventService.getEvent(99L, TEST_IP)
        );

        verifyNoInteractions(eventMapper, statsClient);
    }

    @Test
    void getEvents_shouldReturnPublishedEvents() {
        Event event = createPublishedEvent();

        EventShortDto dto = mock(EventShortDto.class);

        when(eventRepository.findAll(
                any(Specification.class),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of(event)));

        when(eventMapper.toShortDto(event, 0L, 0L))
                .thenReturn(dto);

        List<EventShortDto> result = eventService.getEvents(
                null, null, null, null, null, 0, 10, EventSort.EVENT_DATE
        );

        assertEquals(1, result.size());
        assertSame(dto, result.getFirst());

        verify(statsClient).getStats(
                eq(PUBLISHED_ON),
                any(LocalDateTime.class),
                eq(List.of("/events/1")),
                eq(true)
        );
    }

    @Test
    void getEvents_shouldRejectInvalidPagination() {
        assertThrows(
                IllegalArgumentException.class,
                () -> eventService.getEvents(
                        null, null, null, null, null, -1, 10, EventSort.EVENT_DATE
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> eventService.getEvents(
                        null, null, null, null, null, 0, 0, EventSort.EVENT_DATE
                )
        );

        verifyNoInteractions(eventRepository, statsClient);
    }

    @Test
    void getEvents_shouldRejectInvalidDateRange() {
        LocalDateTime start = LocalDateTime.of(2026, 12, 10, 12, 0);
        LocalDateTime end = LocalDateTime.of(2026, 12, 1, 12, 0);

        assertThrows(
                IllegalArgumentException.class,
                () -> eventService.getEvents(
                        null, null, null, start, end, 0, 10, EventSort.EVENT_DATE
                )
        );

        verifyNoInteractions(eventRepository, statsClient);
    }

    @Test
    void getEvents_shouldReturnViewsForMultipleEvents() {
        Event first = new Event();
        first.setId(1L);
        first.setState(EventState.PUBLISHED);
        first.setPublishedOn(LocalDateTime.of(2026, 11, 20, 12, 0));

        Event second = new Event();
        second.setId(2L);
        second.setState(EventState.PUBLISHED);
        second.setPublishedOn(LocalDateTime.of(2026, 11, 21, 12, 0));

        Event third = new Event();
        third.setId(3L);
        third.setState(EventState.PUBLISHED);
        third.setPublishedOn(LocalDateTime.of(2026, 11, 22, 12, 0));

        ViewStats firstStats = new ViewStats();
        firstStats.setApp("ewm-main-service");
        firstStats.setUri("/events/1");
        firstStats.setHits(5L);

        ViewStats secondStats = new ViewStats();
        secondStats.setApp("ewm-main-service");
        secondStats.setUri("/events/2");
        secondStats.setHits(2L);

        when(eventRepository.findAll(
                any(Specification.class),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of(first, second, third)));

        when(statsClient.getStats(
                eq(first.getPublishedOn()),
                any(LocalDateTime.class),
                eq(List.of("/events/1", "/events/2", "/events/3")),
                eq(true)
        )).thenReturn(List.of(firstStats, secondStats));

        List<EventShortDto> result = eventService.getEvents(
                null, null, null, null, null, 0, 10, EventSort.EVENT_DATE
        );

        assertEquals(3, result.size());

        verify(eventMapper).toShortDto(first, 0L, 5L);
        verify(eventMapper).toShortDto(second, 0L, 2L);
        verify(eventMapper).toShortDto(third, 0L, 0L);

        verify(statsClient).getStats(
                eq(first.getPublishedOn()),
                any(LocalDateTime.class),
                eq(List.of("/events/1", "/events/2", "/events/3")),
                eq(true)
        );
    }

    @Test
    void getEvents_shouldSortByViewsBeforePagination() {
        Event first = createPublishedEvent();
        first.setId(1L);

        Event second = createPublishedEvent();
        second.setId(2L);

        Event third = createPublishedEvent();
        third.setId(3L);

        ViewStats firstStats = new ViewStats();
        firstStats.setUri("/events/1");
        firstStats.setHits(2L);

        ViewStats secondStats = new ViewStats();
        secondStats.setUri("/events/2");
        secondStats.setHits(5L);

        ViewStats thirdStats = new ViewStats();
        thirdStats.setUri("/events/3");
        thirdStats.setHits(10L);

        when(eventRepository.findAll(any(Specification.class)))
                .thenReturn(List.of(first, second, third));

        when(statsClient.getStats(
                eq(PUBLISHED_ON),
                any(LocalDateTime.class),
                eq(List.of("/events/1", "/events/2", "/events/3")),
                eq(true)
        )).thenReturn(List.of(firstStats, secondStats, thirdStats));

        EventShortDto dto = mock(EventShortDto.class);

        when(eventMapper.toShortDto(third, 0L, 10L))
                .thenReturn(dto);

        List<EventShortDto> result = eventService.getEvents(
                null, null, null, null, null,
                0, 1, EventSort.VIEWS
        );

        assertEquals(1, result.size());
        assertSame(dto, result.getFirst());

        verify(eventMapper).toShortDto(third, 0L, 10L);

        verify(eventRepository).findAll(any(Specification.class));

        verify(eventRepository, never()).findAll(
                any(Specification.class),
                any(Pageable.class)
        );

        verify(statsClient, times(1)).getStats(
                eq(PUBLISHED_ON),
                any(LocalDateTime.class),
                eq(List.of("/events/1", "/events/2", "/events/3")),
                eq(true)
        );
    }

    @Test
    void getEvents_shouldReturnSecondPageSortedByViews() {
        Event first = createPublishedEvent();
        first.setId(1L);

        Event second = createPublishedEvent();
        second.setId(2L);

        Event third = createPublishedEvent();
        third.setId(3L);

        ViewStats firstStats = new ViewStats();
        firstStats.setUri("/events/1");
        firstStats.setHits(2L);

        ViewStats secondStats = new ViewStats();
        secondStats.setUri("/events/2");
        secondStats.setHits(5L);

        ViewStats thirdStats = new ViewStats();
        thirdStats.setUri("/events/3");
        thirdStats.setHits(10L);

        when(eventRepository.findAll(any(Specification.class)))
                .thenReturn(List.of(first, second, third));

        when(statsClient.getStats(
                eq(PUBLISHED_ON),
                any(LocalDateTime.class),
                eq(List.of("/events/1", "/events/2", "/events/3")),
                eq(true)
        )).thenReturn(List.of(firstStats, secondStats, thirdStats));

        EventShortDto dto = mock(EventShortDto.class);

        when(eventMapper.toShortDto(second, 0L, 5L))
                .thenReturn(dto);

        List<EventShortDto> result = eventService.getEvents(
                null, null, null, null, null,
                1, 1, EventSort.VIEWS
        );

        assertEquals(1, result.size());
        assertSame(dto, result.getFirst());

        verify(eventMapper).toShortDto(second, 0L, 5L);
    }

    private Event createPublishedEvent() {
        Event event = new Event();
        event.setId(1L);
        event.setState(EventState.PUBLISHED);
        event.setPublishedOn(PUBLISHED_ON);
        return event;
    }
}
