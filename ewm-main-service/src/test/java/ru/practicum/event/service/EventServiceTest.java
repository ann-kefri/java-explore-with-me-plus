package ru.practicum.event.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.practicum.category.model.Category;
import ru.practicum.category.repository.CategoryRepository;
import ru.practicum.event.dto.EventFullDto;
import ru.practicum.event.dto.LocationDto;
import ru.practicum.event.dto.NewEventDto;
import ru.practicum.event.mapper.EventMapper;
import ru.practicum.event.model.Event;
import ru.practicum.event.model.EventState;
import ru.practicum.event.repository.EventRepository;
import ru.practicum.exception.ConflictException;
import ru.practicum.exception.NotFoundException;
import ru.practicum.user.model.User;
import ru.practicum.user.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

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
}
