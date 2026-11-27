package ru.practicum.request.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.event.model.Event;
import ru.practicum.event.model.EventState;
import ru.practicum.event.repository.EventRepository;
import ru.practicum.exception.ConflictException;
import ru.practicum.exception.NotFoundException;
import ru.practicum.request.dto.ConfirmedCount;
import ru.practicum.request.dto.EventRequestStatusUpdateRequest;
import ru.practicum.request.dto.EventRequestStatusUpdateResult;
import ru.practicum.request.dto.ParticipationRequestDto;
import ru.practicum.request.mapper.RequestMapper;
import ru.practicum.request.model.ParticipationRequest;
import ru.practicum.request.model.RequestStatus;
import ru.practicum.request.model.RequestStatusAction;
import ru.practicum.request.repository.ParticipationRequestRepository;
import ru.practicum.user.model.User;
import ru.practicum.user.repository.UserRepository;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RequestService {
    private final ParticipationRequestRepository requestRepository;
    private final UserRepository userRepository;
    private final EventRepository eventRepository;

    @Transactional(readOnly = true)
    public List<ParticipationRequestDto> getRequestsByUserId(Long userId) {
        checkUserExists(userId);
        List<ParticipationRequest> requests = requestRepository.findAllByRequesterId(userId);
        return requests.stream()
                .map(RequestMapper::toDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public ParticipationRequestDto createRequestByEventId(Long userId, Long eventId) {
        User requester = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User with id=" + userId + " was not found"));
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Event with id=" + eventId + " was not found"));
        if (requestRepository.existsByEventIdAndRequesterId(eventId, userId)) {
            throw new ConflictException("Request with event: " + eventId + " and userId: " + userId + "already exist");
        }
        if (userId.equals(event.getInitiator().getId())) {
            throw new ConflictException("Initiator request cant be guest for this request");
        }
        if (!EventState.PUBLISHED.equals(event.getState())) {
            throw new ConflictException("Event is not publihed");
        }
        if (event.getParticipantLimit() > 0) {
            long confirmedRequests = requestRepository.countByEventIdAndStatus(eventId, RequestStatus.CONFIRMED);
            if (confirmedRequests >= event.getParticipantLimit()) {
                throw new ConflictException("Event is all busy. Cause: limit for requests is overall");
            }
        }
        ParticipationRequest request = new ParticipationRequest();
        request.setCreated(LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS));
        request.setRequester(requester);
        request.setEvent(event);
        RequestStatus status = (!event.isRequestModeration() || event.getParticipantLimit() == 0) ?
                RequestStatus.CONFIRMED
                : RequestStatus.PENDING;
        request.setStatus(status);
        try {
            return RequestMapper.toDto(requestRepository.save(request));
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("Requests for event id: " + eventId +
                    " from user id: " + userId + " already exists");
        }
    }

    @Transactional
    public ParticipationRequestDto cancelCurrentUserRequest(Long userId, Long requestId) {
        ParticipationRequest request = requestRepository.findByIdAndRequesterId(requestId, userId)
                .orElseThrow(() ->
                        new NotFoundException("Request: " + requestId + "with user: " + userId + " not found"));
        request.setStatus(RequestStatus.CANCELED);
        return RequestMapper.toDto(requestRepository.save(request));
    }

    @Transactional(readOnly = true)
    public List<ParticipationRequestDto> getRequestsForMyEvent(Long userId, Long eventId) {
        checkUserExists(userId);
        eventCurrentInitiator(eventId, userId);
        return requestRepository.findAllByEventId(eventId).stream()
                .map(RequestMapper::toDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public EventRequestStatusUpdateResult confirmedOrRejected(EventRequestStatusUpdateRequest request, Long userId, Long eventId) {
        Event event = eventCurrentInitiator(eventId, userId);
        List<ParticipationRequest> requests = requestRepository.findAllByIdInAndEventId(request.requestIds(), eventId);
        if (requests.size() != request.requestIds().size()) {
            throw new NotFoundException("Requests not found");
        }
        boolean hasNotPending = requests
                .stream()
                .anyMatch(participationRequest -> participationRequest.getStatus() != RequestStatus.PENDING);
        if (hasNotPending) {
            throw new ConflictException("You can change request have status PENDING");
        }
        List<ParticipationRequestDto> confirmedRequests = new ArrayList<>();
        List<ParticipationRequestDto> rejectedRequests = new ArrayList<>();
        if (request.status() == RequestStatusAction.REJECTED) {
            for (ParticipationRequest req : requests) {
                req.setStatus(RequestStatus.REJECTED);
                rejectedRequests.add(RequestMapper.toDto(req));
            }
            requestRepository.saveAll(requests);
            return new EventRequestStatusUpdateResult(confirmedRequests, rejectedRequests);
        }
        int limit = event.getParticipantLimit();
        if (limit == 0) {
            for (ParticipationRequest req : requests) {
                req.setStatus(RequestStatus.CONFIRMED);
                confirmedRequests.add(RequestMapper.toDto(req));
            }
            requestRepository.saveAll(requests);
            return new EventRequestStatusUpdateResult(confirmedRequests, rejectedRequests);
        }
        long confirmedCount = requestRepository.countByEventIdAndStatus(eventId, RequestStatus.CONFIRMED);
        long freeSlots = limit - confirmedCount;
        if (freeSlots <= 0) {
            throw new ConflictException("Event is full busy");
        }
        for (ParticipationRequest req : requests) {
            if (freeSlots > 0) {
                req.setStatus(RequestStatus.CONFIRMED);
                confirmedRequests.add(RequestMapper.toDto(req));
                freeSlots--;
            } else {
                req.setStatus(RequestStatus.REJECTED);
                rejectedRequests.add(RequestMapper.toDto(req));
            }
        }
        requestRepository.saveAll(requests);
        if (freeSlots == 0) {
            List<ParticipationRequest> otherPending = requestRepository.findAllByEventIdAndStatus(
                    eventId, RequestStatus.PENDING);
            for (ParticipationRequest req : otherPending) {
                req.setStatus(RequestStatus.REJECTED);
                rejectedRequests.add(RequestMapper.toDto(req));
            }
            requestRepository.saveAll(otherPending);
        }
        return new EventRequestStatusUpdateResult(confirmedRequests, rejectedRequests);
    }

    private void checkUserExists(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new NotFoundException(
                    "User with id=" + userId + " was not found"
            );
        }
    }

    private Event eventCurrentInitiator(Long eventId, Long userId) {
        return eventRepository.findByIdAndInitiatorId(eventId, userId)
                .orElseThrow(() -> new NotFoundException(
                        "Event with: " + eventId + "and user with: " + userId + "not found")
                );
    }
    @Transactional(readOnly = true)
    public Map<Long, Long> getConfirmedCounts(Collection<Long> eventIds){
        if (eventIds == null || eventIds.isEmpty()){
            return Map.of();
        }
        return requestRepository.countConfirmedByEventIds(eventIds, RequestStatus.CONFIRMED)
                .stream()
                .collect(Collectors.toMap(ConfirmedCount::eventId, ConfirmedCount::count));
    }
}
