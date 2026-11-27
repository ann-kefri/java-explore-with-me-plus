package ru.practicum.event.mapper;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.practicum.category.mapper.CategoryMapper;
import ru.practicum.category.model.Category;
import ru.practicum.event.dto.EventFullDto;
import ru.practicum.event.dto.EventShortDto;
import ru.practicum.event.dto.LocationDto;
import ru.practicum.event.dto.NewEventDto;
import ru.practicum.event.model.Event;
import ru.practicum.event.model.EventState;
import ru.practicum.event.model.Location;
import ru.practicum.user.dto.UserShortDto;
import ru.practicum.user.model.User;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class EventMapper {

    private final CategoryMapper categoryMapper;

    public Event toEntity(NewEventDto dto, User initiator, Category category) {
        Event event = new Event();

        event.setAnnotation(dto.annotation());
        event.setDescription(dto.description());
        event.setTitle(dto.title());
        event.setCategory(category);
        event.setInitiator(initiator);
        event.setEventDate(dto.eventDate());

        event.setLocation(new Location(
                dto.location().lat(),
                dto.location().lon()
        ));

        event.setPaid(dto.paid());
        event.setParticipantLimit(dto.participantLimit());
        event.setRequestModeration(dto.requestModeration());

        event.setCreatedOn(LocalDateTime.now());
        event.setState(EventState.PENDING);

        return event;
    }

    public EventFullDto toFullDto(Event event, long confirmedRequests, long views) {
        return new EventFullDto(
                event.getId(),
                event.getAnnotation(),
                categoryMapper.toCategoryDto(event.getCategory()),
                confirmedRequests,
                event.getCreatedOn(),
                event.getDescription(),
                event.getEventDate(),
                toUserShortDto(event.getInitiator()),
                toLocationDto(event.getLocation()),
                event.isPaid(),
                event.getParticipantLimit(),
                event.getPublishedOn(),
                event.isRequestModeration(),
                event.getState(),
                event.getTitle(),
                views
        );
    }

    public EventShortDto toShortDto(
            Event event,
            long confirmedRequests,
            long views
    ) {
        return new EventShortDto(
                event.getId(),
                event.getAnnotation(),
                categoryMapper.toCategoryDto(event.getCategory()),
                confirmedRequests,
                event.getEventDate(),
                new UserShortDto(
                        event.getInitiator().getId(),
                        event.getInitiator().getName()
                ),
                event.isPaid(),
                event.getTitle(),
                views
        );
    }

    private UserShortDto toUserShortDto(User user) {
        return new UserShortDto(
                user.getId(),
                user.getName()
        );
    }

    private LocationDto toLocationDto(Location location) {
        return new LocationDto(
                location.getLat(),
                location.getLon()
        );
    }
}
