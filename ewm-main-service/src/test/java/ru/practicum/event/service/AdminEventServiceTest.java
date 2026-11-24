package ru.practicum.event.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.practicum.category.repository.CategoryRepository;
import ru.practicum.event.dto.UpdateEventAdminRequest;
import ru.practicum.event.mapper.EventMapper;
import ru.practicum.event.model.AdminEventStateAction;
import ru.practicum.event.model.Event;
import ru.practicum.event.model.EventState;
import ru.practicum.event.repository.EventRepository;
import ru.practicum.exception.ConflictException;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminEventServiceTest {

    @Mock
    private EventRepository eventRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private EventMapper eventMapper;

    private AdminEventService eventService;

    @BeforeEach
    void setUp() {
        EventUpdater eventUpdater = new EventUpdater(categoryRepository);

        eventService = new AdminEventService(
                eventRepository,
                eventMapper,
                eventUpdater
        );
    }

    @Test
    void updateAdminEvent_shouldPublishPendingEvent() {
        Event event = createPendingEvent();

        UpdateEventAdminRequest request = adminRequest(
                null, AdminEventStateAction.PUBLISH_EVENT
        );

        when(eventRepository.findById(1L)).thenReturn(Optional.of(event));
        when(eventRepository.save(event)).thenReturn(event);

        eventService.updateAdminEvent(1L, request);

        assertEquals(EventState.PUBLISHED, event.getState());
        assertNotNull(event.getPublishedOn());
        verify(eventRepository).save(event);
    }

    @Test
    void updateAdminEvent_shouldRejectAlreadyPublishedEvent() {
        Event event = createPendingEvent();
        event.setState(EventState.PUBLISHED);

        UpdateEventAdminRequest request = adminRequest(
                null, AdminEventStateAction.PUBLISH_EVENT
        );

        when(eventRepository.findById(1L)).thenReturn(Optional.of(event));

        assertThrows(
                ConflictException.class,
                () -> eventService.updateAdminEvent(1L, request)
        );

        verify(eventRepository, never()).save(any());
    }

    @Test
    void updateAdminEvent_shouldCancelPendingEvent() {
        Event event = createPendingEvent();

        UpdateEventAdminRequest request = adminRequest(
                null, AdminEventStateAction.REJECT_EVENT
        );

        when(eventRepository.findById(1L)).thenReturn(Optional.of(event));
        when(eventRepository.save(event)).thenReturn(event);

        eventService.updateAdminEvent(1L, request);

        assertEquals(EventState.CANCELED, event.getState());
        assertNull(event.getPublishedOn());
        verify(eventRepository).save(event);
    }

    @Test
    void updateAdminEvent_shouldNotRejectPublishedEvent() {
        Event event = createPendingEvent();
        event.setState(EventState.PUBLISHED);

        UpdateEventAdminRequest request = adminRequest(
                null, AdminEventStateAction.REJECT_EVENT
        );

        when(eventRepository.findById(1L)).thenReturn(Optional.of(event));

        assertThrows(
                ConflictException.class,
                () -> eventService.updateAdminEvent(1L, request)
        );

        verify(eventRepository, never()).save(any());
    }

    @Test
    void updateAdminEvent_shouldPublishWithUpdatedDate() {
        Event event = createPendingEvent();
        LocalDateTime newDate = LocalDateTime.now().plusDays(3);

        UpdateEventAdminRequest request = adminRequest(
                newDate, AdminEventStateAction.PUBLISH_EVENT
        );

        when(eventRepository.findById(1L)).thenReturn(Optional.of(event));
        when(eventRepository.save(event)).thenReturn(event);

        eventService.updateAdminEvent(1L, request);

        assertEquals(newDate, event.getEventDate());
        assertEquals(EventState.PUBLISHED, event.getState());
        assertNotNull(event.getPublishedOn());
    }

    @Test
    void updateAdminEvent_shouldRejectPublicationTooCloseToStart() {
        Event event = createPendingEvent();
        event.setEventDate(LocalDateTime.now().plusMinutes(30));

        UpdateEventAdminRequest request = adminRequest(
                null, AdminEventStateAction.PUBLISH_EVENT
        );

        when(eventRepository.findById(1L)).thenReturn(Optional.of(event));

        assertThrows(
                ConflictException.class,
                () -> eventService.updateAdminEvent(1L, request)
        );

        assertEquals(EventState.PENDING, event.getState());
        verify(eventRepository, never()).save(any());
    }

    private UpdateEventAdminRequest adminRequest(
            LocalDateTime eventDate,
            AdminEventStateAction stateAction
    ) {
        return new UpdateEventAdminRequest(
                null,
                null,
                null,
                eventDate,
                null,
                null,
                null,
                null,
                stateAction,
                null
        );
    }

    private Event createPendingEvent() {
        Event event = new Event();
        event.setId(1L);
        event.setTitle("Музыкальный вечер");
        event.setDescription("Исходное описание события");
        event.setPaid(true);
        event.setEventDate(LocalDateTime.now().plusDays(1));
        event.setState(EventState.PENDING);
        return event;
    }
}
