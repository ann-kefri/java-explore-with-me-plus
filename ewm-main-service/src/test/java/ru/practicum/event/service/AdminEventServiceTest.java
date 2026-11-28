package ru.practicum.event.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import ru.practicum.category.repository.CategoryRepository;
import ru.practicum.event.dto.EventFullDto;
import ru.practicum.event.dto.UpdateEventAdminRequest;
import ru.practicum.event.mapper.EventMapper;
import ru.practicum.event.model.AdminEventStateAction;
import ru.practicum.event.model.Event;
import ru.practicum.event.model.EventState;
import ru.practicum.event.repository.EventRepository;
import ru.practicum.exception.ConflictException;
import ru.practicum.request.model.RequestStatus;
import ru.practicum.request.repository.ConfirmedRequestCount;
import ru.practicum.request.repository.ParticipationRequestRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminEventServiceTest {

    @Mock
    private EventRepository eventRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private EventMapper eventMapper;

    @Mock
    private ParticipationRequestRepository requestRepository;

    private AdminEventService eventService;

    @BeforeEach
    void setUp() {
        EventUpdater eventUpdater = new EventUpdater(categoryRepository);

        eventService = new AdminEventService(
                eventRepository,
                requestRepository,
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

        verify(requestRepository).countByEventIdAndStatus(
                1L,
                RequestStatus.CONFIRMED
        );
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

        verify(requestRepository).countByEventIdAndStatus(
                1L,
                RequestStatus.CONFIRMED
        );
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

        verify(requestRepository).countByEventIdAndStatus(
                1L,
                RequestStatus.CONFIRMED
        );
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

    @Test
    void getAdminEvents_shouldReturnEventsWithoutFilters() {
        Event first = createPendingEvent();

        Event second = createPendingEvent();
        second.setId(2L);
        second.setState(EventState.CANCELED);

        when(eventRepository.findAll(
                any(Specification.class),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of(first, second)));

        when(requestRepository.countByEventIdsAndStatus(
                eq(List.of(1L, 2L)),
                eq(RequestStatus.CONFIRMED)
        )).thenReturn(List.of());

        List<EventFullDto> result = eventService.getAdminEvents(
                null, null, null, null, null, 0, 10
        );

        verify(requestRepository).countByEventIdsAndStatus(
                List.of(1L, 2L),
                RequestStatus.CONFIRMED
        );

        assertEquals(2, result.size());

        verify(eventRepository).findAll(
                any(Specification.class),
                any(Pageable.class)
        );
    }

    @Test
    void getAdminEvents_shouldUseExactOffset() {
        when(eventRepository.findAll(
                any(Specification.class),
                any(Pageable.class)
        )).thenReturn(Page.empty());

        eventService.getAdminEvents(
                List.of(1L),
                List.of(EventState.PUBLISHED),
                List.of(1L),
                null,
                null,
                5,
                10
        );

        ArgumentCaptor<Pageable> captor =
                ArgumentCaptor.forClass(Pageable.class);

        verify(eventRepository).findAll(
                any(Specification.class),
                captor.capture()
        );

        assertEquals(5, captor.getValue().getOffset());
        assertEquals(10, captor.getValue().getPageSize());
    }

    @Test
    void getAdminEvents_shouldRejectInvalidDateRange() {
        LocalDateTime start = LocalDateTime.now().plusDays(2);
        LocalDateTime end = LocalDateTime.now().plusDays(1);

        assertThrows(
                IllegalArgumentException.class,
                () -> eventService.getAdminEvents(
                        null, null, null, start, end, 0, 10
                )
        );

        verifyNoInteractions(eventRepository);
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

    @Test
    void getAdminEvents_shouldReturnConfirmedRequestsForMultipleEvents() {
        Event first = createPendingEvent();

        Event second = createPendingEvent();
        second.setId(2L);

        when(eventRepository.findAll(
                any(Specification.class),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of(first, second)));

        ConfirmedRequestCount firstCount = new ConfirmedRequestCount() {
            @Override
            public Long getEventId() {
                return 1L;
            }

            @Override
            public Long getCount() {
                return 3L;
            }
        };

        when(requestRepository.countByEventIdsAndStatus(
                List.of(1L, 2L),
                RequestStatus.CONFIRMED
        )).thenReturn(List.of(firstCount));

        eventService.getAdminEvents(
                null, null, null, null, null, 0, 10
        );

        verify(eventMapper).toFullDto(first, 3L, 0L);
        verify(eventMapper).toFullDto(second, 0L, 0L);

        verify(requestRepository, times(1))
                .countByEventIdsAndStatus(
                        List.of(1L, 2L),
                        RequestStatus.CONFIRMED
                );
    }

    @Test
    void updateAdminEvent_shouldReturnConfirmedRequests() {
        Event event = createPendingEvent();

        UpdateEventAdminRequest request = adminRequest(
                null,
                AdminEventStateAction.PUBLISH_EVENT
        );

        when(eventRepository.findById(1L))
                .thenReturn(Optional.of(event));

        when(eventRepository.save(event))
                .thenReturn(event);

        when(requestRepository.countByEventIdAndStatus(
                1L,
                RequestStatus.CONFIRMED
        )).thenReturn(5L);

        eventService.updateAdminEvent(1L, request);

        verify(eventMapper).toFullDto(event, 5L, 0L);
    }

    @Test
    void updateAdminEvent_shouldRejectInvalidEventDate() {
        Event event = createPendingEvent();

        UpdateEventAdminRequest request = adminRequest(
                LocalDateTime.now().minusDays(1),
                null
        );

        when(eventRepository.findById(1L))
                .thenReturn(Optional.of(event));

        assertThrows(
                IllegalArgumentException.class,
                () -> eventService.updateAdminEvent(1L, request)
        );

        verify(eventRepository, never()).save(any());
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
