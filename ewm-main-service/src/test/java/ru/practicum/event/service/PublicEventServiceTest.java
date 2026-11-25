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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublicEventServiceTest {

    @Mock
    private EventRepository eventRepository;

    @Mock
    private EventMapper eventMapper;

    @InjectMocks
    private PublicEventService eventService;

    @Test
    void getEvent_shouldReturnPublishedEvent() {
        Event event = new Event();
        event.setId(1L);
        event.setState(EventState.PUBLISHED);

        EventFullDto dto = mock(EventFullDto.class);

        when(eventRepository.findById(1L))
                .thenReturn(Optional.of(event));
        when(eventMapper.toFullDto(event, 0L, 0L))
                .thenReturn(dto);

        EventFullDto result = eventService.getEvent(1L);

        assertSame(dto, result);
        verify(eventRepository).findById(1L);
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
                () -> eventService.getEvent(2L)
        );

        verifyNoInteractions(eventMapper);
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
                () -> eventService.getEvent(3L)
        );

        verifyNoInteractions(eventMapper);
    }

    @Test
    void getEvent_shouldReturnNotFoundForUnknownId() {
        when(eventRepository.findById(99L))
                .thenReturn(Optional.empty());

        assertThrows(
                NotFoundException.class,
                () -> eventService.getEvent(99L)
        );

        verifyNoInteractions(eventMapper);
    }

    @Test
    void getEvents_shouldReturnPublishedEvents() {
        Event event = new Event();
        event.setId(1L);
        event.setState(EventState.PUBLISHED);

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
        assertSame(dto, result.get(0));
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

        verifyNoInteractions(eventRepository);
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

        verifyNoInteractions(eventRepository);
    }
}
