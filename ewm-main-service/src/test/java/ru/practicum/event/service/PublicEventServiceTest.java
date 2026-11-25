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
                null, null, null, null, null, 0, 10
        );

        assertEquals(1, result.size());
        assertSame(dto, result.getFirst());

        verifyNoInteractions(statsClient);
    }

    @Test
    void getEvents_shouldRejectInvalidPagination() {
        assertThrows(
                IllegalArgumentException.class,
                () -> eventService.getEvents(
                        null, null, null, null, null, -1, 10
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> eventService.getEvents(
                        null, null, null, null, null, 0, 0
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
                        null, null, null, start, end, 0, 10
                )
        );

        verifyNoInteractions(eventRepository, statsClient);
    }

    private Event createPublishedEvent() {
        Event event = new Event();
        event.setId(1L);
        event.setState(EventState.PUBLISHED);
        event.setPublishedOn(PUBLISHED_ON);
        return event;
    }
}
