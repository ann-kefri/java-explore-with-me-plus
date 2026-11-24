package ru.practicum.event.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.practicum.event.dto.EventFullDto;
import ru.practicum.event.dto.UpdateEventAdminRequest;
import ru.practicum.event.service.AdminEventService;

@RestController
@RequestMapping("/admin/events")
@RequiredArgsConstructor
public class AdminEventController {

    private final AdminEventService eventService;

    @PatchMapping("/{eventId}")
    public EventFullDto updateEvent(
            @PathVariable Long eventId,
            @Valid @RequestBody UpdateEventAdminRequest dto
    ) {
        return eventService.updateAdminEvent(eventId, dto);
    }
}
