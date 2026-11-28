package ru.practicum.event.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.category.model.Category;
import ru.practicum.category.repository.CategoryRepository;
import ru.practicum.common.OffsetPageRequest;
import ru.practicum.event.dto.EventFullDto;
import ru.practicum.event.dto.NewEventDto;
import ru.practicum.event.dto.UpdateEventUserRequest;
import ru.practicum.event.mapper.EventMapper;
import ru.practicum.event.model.Event;
import ru.practicum.event.model.EventState;
import ru.practicum.event.repository.EventRepository;
import ru.practicum.exception.ConflictException;
import ru.practicum.exception.NotFoundException;
import ru.practicum.user.model.User;
import ru.practicum.user.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PrivateEventService {

    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final EventMapper eventMapper;
    private final EventUpdater eventUpdater;

    @Transactional
    public EventFullDto createEvent(Long userId, NewEventDto dto) {
        User initiator = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException(
                        "User with id=" + userId + " was not found"
                ));

        Category category = categoryRepository.findById(dto.category())
                .orElseThrow(() -> new NotFoundException(
                        "Category with id=" + dto.category() + " was not found"
                ));

        if (dto.eventDate().isBefore(LocalDateTime.now().plusHours(2))) {
            throw new IllegalArgumentException(
                    "Event date must be at least two hours in the future"
            );
        }

        Event event = eventMapper.toEntity(dto, initiator, category);
        Event savedEvent = eventRepository.save(event);

        return eventMapper.toFullDto(savedEvent, 0L, 0L);
    }

    @Transactional(readOnly = true)
    public List<EventFullDto> getUserEvents(Long userId, int from, int size) {
        if (from < 0 || size < 1) {
            throw new IllegalArgumentException(
                    "Pagination parameters must satisfy from >= 0 and size > 0"
            );
        }

        checkUserExists(userId);

        Pageable pageable = new OffsetPageRequest(
                from,
                size,
                Sort.by(Sort.Direction.ASC, "id")
        );

        return eventRepository.findAllByInitiatorId(userId, pageable)
                .stream()
                .map(event -> eventMapper.toFullDto(event, 0L, 0L))
                .toList();
    }

    @Transactional(readOnly = true)
    public EventFullDto getUserEvent(Long userId, Long eventId) {
        checkUserExists(userId);

        Event event = eventRepository.findByIdAndInitiatorId(eventId, userId)
                .orElseThrow(() -> new NotFoundException(
                        "Event with id=" + eventId
                        + " was not found for user with id=" + userId
                ));

        return eventMapper.toFullDto(event, 0L, 0L);
    }

    @Transactional
    public EventFullDto updateUserEvent(
            Long userId,
            Long eventId,
            UpdateEventUserRequest dto
    ) {
        checkUserExists(userId);

        Event event = eventRepository.findByIdAndInitiatorId(eventId, userId)
                .orElseThrow(() -> new NotFoundException(
                        "Event with id=" + eventId
                        + " was not found for user with id=" + userId
                ));

        if (event.getState() == EventState.PUBLISHED) {
            throw new ConflictException(
                    "Only pending or canceled events can be changed"
            );
        }

        eventUpdater.apply(event, dto);

        if (dto.eventDate() != null) {
            if (dto.eventDate().isBefore(LocalDateTime.now().plusHours(2))) {
                throw new IllegalArgumentException(
                        "Event date must be at least two hours in the future"
                );
            }

            event.setEventDate(dto.eventDate());
        }

        if (dto.stateAction() != null) {
            switch (dto.stateAction()) {
                case SEND_TO_REVIEW -> event.setState(EventState.PENDING);
                case CANCEL_REVIEW -> event.setState(EventState.CANCELED);
            }
        }

        Event savedEvent = eventRepository.save(event);

        return eventMapper.toFullDto(savedEvent, 0L, 0L);
    }

    private void checkUserExists(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new NotFoundException(
                    "User with id=" + userId + " was not found"
            );
        }
    }
}
