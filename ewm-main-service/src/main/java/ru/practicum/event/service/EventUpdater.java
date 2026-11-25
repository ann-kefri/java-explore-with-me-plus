package ru.practicum.event.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.practicum.category.model.Category;
import ru.practicum.category.repository.CategoryRepository;
import ru.practicum.event.dto.LocationDto;
import ru.practicum.event.dto.UpdateEventAdminRequest;
import ru.practicum.event.dto.UpdateEventUserRequest;
import ru.practicum.event.model.Event;
import ru.practicum.event.model.Location;
import ru.practicum.exception.NotFoundException;

@Component
@RequiredArgsConstructor
public class EventUpdater {

    private final CategoryRepository categoryRepository;

    public void apply(Event event, UpdateEventUserRequest dto) {
        applyCommon(
                event,
                dto.annotation(),
                dto.category(),
                dto.description(),
                dto.location(),
                dto.paid(),
                dto.participantLimit(),
                dto.requestModeration(),
                dto.title()
        );
    }

    public void apply(Event event, UpdateEventAdminRequest dto) {
        applyCommon(
                event,
                dto.annotation(),
                dto.category(),
                dto.description(),
                dto.location(),
                dto.paid(),
                dto.participantLimit(),
                dto.requestModeration(),
                dto.title()
        );
    }

    private void applyCommon(
            Event event,
            String annotation,
            Long categoryId,
            String description,
            LocationDto location,
            Boolean paid,
            Integer participantLimit,
            Boolean requestModeration,
            String title
    ) {
        if (annotation != null) {
            event.setAnnotation(annotation);
        }

        if (categoryId != null) {
            Category category = categoryRepository.findById(categoryId)
                    .orElseThrow(() -> new NotFoundException(
                            "Category with id=" + categoryId + " was not found"
                    ));

            event.setCategory(category);
        }

        if (description != null) {
            event.setDescription(description);
        }

        if (location != null) {
            event.setLocation(new Location(
                    location.lat(),
                    location.lon()
            ));
        }

        if (paid != null) {
            event.setPaid(paid);
        }

        if (participantLimit != null) {
            event.setParticipantLimit(participantLimit);
        }

        if (requestModeration != null) {
            event.setRequestModeration(requestModeration);
        }

        if (title != null) {
            event.setTitle(title);
        }
    }
}
