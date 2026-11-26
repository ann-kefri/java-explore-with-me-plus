package ru.practicum.request.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import ru.practicum.request.dto.EventRequestStatusUpdateRequest;
import ru.practicum.request.dto.EventRequestStatusUpdateResult;
import ru.practicum.request.dto.ParticipationRequestDto;
import ru.practicum.request.service.RequestService;

import java.util.List;

@RestController
@RequestMapping("/users/{userId}/events/{eventId}/requests")
@RequiredArgsConstructor
public class UsersEventsRequestsController {
    private final RequestService requestService;

    @GetMapping
    public List<ParticipationRequestDto> getRequestsByEventForCurrentUser(@PathVariable Long userId, @PathVariable Long eventId) {
        return requestService.getRequestsForMyEvent(userId, eventId);
    }

    @PatchMapping
    public EventRequestStatusUpdateResult ConOrRejStatusRequests(
            @RequestBody @Valid EventRequestStatusUpdateRequest request,
            @PathVariable Long userId,
            @PathVariable Long eventId) {
        return requestService.confirmedOrRejected(request, userId, eventId);
    }

}
