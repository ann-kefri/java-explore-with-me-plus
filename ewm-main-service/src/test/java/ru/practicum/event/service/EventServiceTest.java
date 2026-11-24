package ru.practicum.event.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import ru.practicum.category.model.Category;
import ru.practicum.category.repository.CategoryRepository;
import ru.practicum.event.dto.EventFullDto;
import ru.practicum.event.dto.LocationDto;
import ru.practicum.event.dto.NewEventDto;
import ru.practicum.event.dto.UpdateEventUserRequest;
import ru.practicum.event.mapper.EventMapper;
import ru.practicum.event.model.Event;
import ru.practicum.event.model.EventState;
import ru.practicum.event.model.UserEventStateAction;
import ru.practicum.event.repository.EventRepository;
import ru.practicum.exception.ConflictException;
import ru.practicum.exception.NotFoundException;
import ru.practicum.user.model.User;
import ru.practicum.user.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    @Mock
    private EventRepository eventRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private EventMapper eventMapper;

    @InjectMocks
    private EventService eventService;

    private NewEventDto dto;

    @BeforeEach
    void setUp() {
        dto = new NewEventDto(
                "Большой концерт современной инструментальной музыки",
                1L,
                "Вечер инструментальной музыки с участием известных исполнителей.",
                LocalDateTime.now().plusDays(1),
                new LocationDto(55.75f, 37.62f),
                false,
                0,
                true,
                "Музыкальный вечер"
        );
    }

    @Test
    void createEvent_shouldSaveEvent() {
        User user = new User();
        user.setId(1L);

        Category category = new Category();
        category.setId(1L);

        Event event = new Event();
        event.setState(EventState.PENDING);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(eventMapper.toEntity(dto, user, category)).thenReturn(event);
        when(eventRepository.save(event)).thenReturn(event);

        EventFullDto expected = mock(EventFullDto.class);
        when(eventMapper.toFullDto(event, 0L, 0L)).thenReturn(expected);

        EventFullDto result = eventService.createEvent(1L, dto);

        assertSame(expected, result);
        verify(eventRepository).save(event);
        verify(eventMapper).toFullDto(event, 0L, 0L);
    }

    @Test
    void createEvent_shouldThrowWhenUserNotFound() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(
                NotFoundException.class,
                () -> eventService.createEvent(999L, dto)
        );

        verify(eventRepository, never()).save(any());
    }

    @Test
    void createEvent_shouldThrowWhenCategoryNotFound() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(new User()));
        when(categoryRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(
                NotFoundException.class,
                () -> eventService.createEvent(1L, dto)
        );

        verify(eventRepository, never()).save(any());
    }

    @Test
    void createEvent_shouldRejectEventStartingTooSoon() {
        NewEventDto invalidDto = new NewEventDto(
                dto.annotation(),
                dto.category(),
                dto.description(),
                LocalDateTime.now().plusMinutes(30),
                dto.location(),
                dto.paid(),
                dto.participantLimit(),
                dto.requestModeration(),
                dto.title()
        );

        when(userRepository.findById(1L)).thenReturn(Optional.of(new User()));
        when(categoryRepository.findById(1L))
                .thenReturn(Optional.of(new Category()));

        assertThrows(
                ConflictException.class,
                () -> eventService.createEvent(1L, invalidDto)
        );

        verify(eventRepository, never()).save(any());
    }

    @Test
    void updateUserEvent_shouldUpdateOnlyProvidedFields() {
        Event event = createPendingEvent();

        UpdateEventUserRequest request = new UpdateEventUserRequest(
                null, null, null, null, null,
                false, null, null, null,
                "Новое название"
        );

        when(userRepository.existsById(1L)).thenReturn(true);
        when(eventRepository.findByIdAndInitiatorId(1L, 1L))
                .thenReturn(Optional.of(event));
        when(eventRepository.save(event)).thenReturn(event);

        eventService.updateUserEvent(1L, 1L, request);

        assertEquals("Новое название", event.getTitle());
        assertFalse(event.isPaid());
        assertEquals("Исходное описание события", event.getDescription());

        verify(eventRepository).save(event);
    }

    @Test
    void updateUserEvent_shouldCancelReview() {
        Event event = createPendingEvent();

        UpdateEventUserRequest request = new UpdateEventUserRequest(
                null, null, null, null, null,
                null, null, null,
                UserEventStateAction.CANCEL_REVIEW,
                null
        );

        when(userRepository.existsById(1L)).thenReturn(true);
        when(eventRepository.findByIdAndInitiatorId(1L, 1L))
                .thenReturn(Optional.of(event));
        when(eventRepository.save(event)).thenReturn(event);

        eventService.updateUserEvent(1L, 1L, request);

        assertEquals(EventState.CANCELED, event.getState());
        verify(eventRepository).save(event);
    }

    @Test
    void updateUserEvent_shouldRejectPublishedEvent() {
        Event event = createPendingEvent();
        event.setState(EventState.PUBLISHED);

        UpdateEventUserRequest request = new UpdateEventUserRequest(
                null, null, null, null, null,
                null, null, null, null,
                "Новое название"
        );

        when(userRepository.existsById(1L)).thenReturn(true);
        when(eventRepository.findByIdAndInitiatorId(1L, 1L))
                .thenReturn(Optional.of(event));

        assertThrows(
                ConflictException.class,
                () -> eventService.updateUserEvent(1L, 1L, request)
        );

        verify(eventRepository, never()).save(any());
        assertEquals("Музыкальный вечер", event.getTitle());
    }

    @Test
    void updateUserEvent_shouldRejectEventStartingTooSoon() {
        Event event = createPendingEvent();

        UpdateEventUserRequest request = new UpdateEventUserRequest(
                null, null, null,
                LocalDateTime.now().plusMinutes(30),
                null, null, null, null, null, null
        );

        when(userRepository.existsById(1L)).thenReturn(true);
        when(eventRepository.findByIdAndInitiatorId(1L, 1L))
                .thenReturn(Optional.of(event));

        assertThrows(
                ConflictException.class,
                () -> eventService.updateUserEvent(1L, 1L, request)
        );

        verify(eventRepository, never()).save(any());
    }

    @Test
    void getUserEvents_shouldReturnUserEvents() {
        Event event = createPendingEvent();
        EventFullDto expected = mock(EventFullDto.class);

        when(userRepository.existsById(1L)).thenReturn(true);
        when(eventRepository.findAllByInitiatorId(eq(1L), any(Pageable.class)))
                .thenReturn(List.of(event));
        when(eventMapper.toFullDto(event, 0L, 0L)).thenReturn(expected);

        List<EventFullDto> result = eventService.getUserEvents(1L, 0, 10);

        assertEquals(1, result.size());
        assertSame(expected, result.getFirst());

        verify(eventRepository).findAllByInitiatorId(
                eq(1L),
                argThat(pageable ->
                        pageable.getOffset() == 0
                        && pageable.getPageSize() == 10
                )
        );
    }

    @Test
    void getUserEvents_shouldUseExactOffset() {
        when(userRepository.existsById(1L)).thenReturn(true);
        when(eventRepository.findAllByInitiatorId(eq(1L), any(Pageable.class)))
                .thenReturn(List.of());

        eventService.getUserEvents(1L, 5, 10);

        verify(eventRepository).findAllByInitiatorId(
                eq(1L),
                argThat(pageable ->
                        pageable.getOffset() == 5
                        && pageable.getPageSize() == 10
                )
        );
    }

    @Test
    void getUserEvent_shouldReturnEvent() {
        Event event = createPendingEvent();
        EventFullDto expected = mock(EventFullDto.class);

        when(userRepository.existsById(1L)).thenReturn(true);
        when(eventRepository.findByIdAndInitiatorId(1L, 1L))
                .thenReturn(Optional.of(event));
        when(eventMapper.toFullDto(event, 0L, 0L)).thenReturn(expected);

        EventFullDto result = eventService.getUserEvent(1L, 1L);

        assertSame(expected, result);
    }

    @Test
    void getUserEvent_shouldThrowWhenEventNotOwnedByUser() {
        when(userRepository.existsById(1L)).thenReturn(true);
        when(eventRepository.findByIdAndInitiatorId(2L, 1L))
                .thenReturn(Optional.empty());

        assertThrows(
                NotFoundException.class,
                () -> eventService.getUserEvent(1L, 2L)
        );

        verify(eventMapper, never()).toFullDto(any(), anyLong(), anyLong());
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
