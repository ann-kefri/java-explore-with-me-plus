package ru.practicum.request.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import ru.practicum.event.model.Event;
import ru.practicum.event.model.EventState;
import ru.practicum.event.repository.EventRepository;
import ru.practicum.exception.ConflictException;
import ru.practicum.exception.NotFoundException;
import ru.practicum.request.dto.EventRequestStatusUpdateRequest;
import ru.practicum.request.dto.EventRequestStatusUpdateResult;
import ru.practicum.request.dto.ParticipationRequestDto;
import ru.practicum.request.model.ParticipationRequest;
import ru.practicum.request.model.RequestStatus;
import ru.practicum.request.model.RequestStatusAction;
import ru.practicum.request.repository.ParticipationRequestRepository;
import ru.practicum.user.model.User;
import ru.practicum.user.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RequestServiceTest {

    private static final Long INITIATOR_ID = 1L;
    private static final Long USER_ID = 2L;
    private static final Long EVENT_ID = 10L;

    @Mock
    private ParticipationRequestRepository requestRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private EventRepository eventRepository;

    private RequestService requestService;

    private User initiator;
    private User user;
    private Event event;

    @BeforeEach
    void setUp() {
        requestService = new RequestService(requestRepository, userRepository, eventRepository);

        initiator = createUser(INITIATOR_ID);
        user = createUser(USER_ID);
        event = createEvent(EVENT_ID, initiator, EventState.PUBLISHED, 10, true);
    }


    @Test
    void getRequestsByUserId_shouldThrowNotFound_whenUserMissing() {
        when(userRepository.existsById(USER_ID)).thenReturn(false);

        assertThrows(NotFoundException.class, () -> requestService.getRequestsByUserId(USER_ID));
        verify(requestRepository, never()).findAllByRequesterId(any());
    }

    @Test
    void getRequestsByUserId_shouldReturnUserRequests() {
        ParticipationRequest request = createRequest(100L, RequestStatus.PENDING);
        when(userRepository.existsById(USER_ID)).thenReturn(true);
        when(requestRepository.findAllByRequesterId(USER_ID)).thenReturn(List.of(request));

        List<ParticipationRequestDto> result = requestService.getRequestsByUserId(USER_ID);

        assertEquals(1, result.size());
        assertEquals(100L, result.get(0).id());
        assertEquals(EVENT_ID, result.get(0).event());
        assertEquals(USER_ID, result.get(0).requester());
    }


    @Test
    void create_shouldThrowNotFound_whenUserMissing() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class,
                () -> requestService.createRequestByEventId(USER_ID, EVENT_ID));
    }

    @Test
    void create_shouldThrowNotFound_whenEventMissing() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class,
                () -> requestService.createRequestByEventId(USER_ID, EVENT_ID));
    }

    @Test
    void create_shouldThrowConflict_whenRequestAlreadyExists() {
        stubUserAndEvent();
        when(requestRepository.existsByEventIdAndRequesterId(EVENT_ID, USER_ID)).thenReturn(true);

        assertThrows(ConflictException.class,
                () -> requestService.createRequestByEventId(USER_ID, EVENT_ID));
        verify(requestRepository, never()).saveAndFlush(any());
    }

    @Test
    void create_shouldThrowConflict_whenUserIsInitiator() {
        when(userRepository.findById(INITIATOR_ID)).thenReturn(Optional.of(initiator));
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));
        when(requestRepository.existsByEventIdAndRequesterId(EVENT_ID, INITIATOR_ID)).thenReturn(false);

        assertThrows(ConflictException.class,
                () -> requestService.createRequestByEventId(INITIATOR_ID, EVENT_ID));
        verify(requestRepository, never()).saveAndFlush(any());
    }

    @Test
    void create_shouldThrowConflict_whenEventNotPublished() {
        event.setState(EventState.PENDING);
        stubUserAndEvent();
        when(requestRepository.existsByEventIdAndRequesterId(EVENT_ID, USER_ID)).thenReturn(false);

        assertThrows(ConflictException.class,
                () -> requestService.createRequestByEventId(USER_ID, EVENT_ID));
        verify(requestRepository, never()).saveAndFlush(any());
    }

    @Test
    void create_shouldThrowConflict_whenLimitReached() {
        event.setParticipantLimit(2);
        stubUserAndEvent();
        when(requestRepository.existsByEventIdAndRequesterId(EVENT_ID, USER_ID)).thenReturn(false);
        when(requestRepository.countByEventIdAndStatus(EVENT_ID, RequestStatus.CONFIRMED)).thenReturn(2L);

        assertThrows(ConflictException.class,
                () -> requestService.createRequestByEventId(USER_ID, EVENT_ID));
        verify(requestRepository, never()).saveAndFlush(any());
    }

    @Test
    void create_shouldBePending_whenModerationOnAndLimitSet() {
        stubUserAndEvent();
        when(requestRepository.existsByEventIdAndRequesterId(EVENT_ID, USER_ID)).thenReturn(false);
        when(requestRepository.countByEventIdAndStatus(EVENT_ID, RequestStatus.CONFIRMED)).thenReturn(0L);
        stubSave();

        ParticipationRequestDto result = requestService.createRequestByEventId(USER_ID, EVENT_ID);

        assertEquals(RequestStatus.PENDING, result.status());
        assertEquals(EVENT_ID, result.event());
        assertEquals(USER_ID, result.requester());
    }

    @Test
    void create_shouldBeConfirmed_whenModerationOff() {
        event.setRequestModeration(false);
        stubUserAndEvent();
        when(requestRepository.existsByEventIdAndRequesterId(EVENT_ID, USER_ID)).thenReturn(false);
        when(requestRepository.countByEventIdAndStatus(EVENT_ID, RequestStatus.CONFIRMED)).thenReturn(0L);
        stubSave();

        ParticipationRequestDto result = requestService.createRequestByEventId(USER_ID, EVENT_ID);

        assertEquals(RequestStatus.CONFIRMED, result.status());
    }

    @Test
    void create_shouldBeConfirmed_whenLimitIsZero() {
        event.setParticipantLimit(0);
        stubUserAndEvent();
        when(requestRepository.existsByEventIdAndRequesterId(EVENT_ID, USER_ID)).thenReturn(false);
        stubSave();

        ParticipationRequestDto result = requestService.createRequestByEventId(USER_ID, EVENT_ID);

        assertEquals(RequestStatus.CONFIRMED, result.status());
        verify(requestRepository, never()).countByEventIdAndStatus(any(), any());
    }

    @Test
    void create_shouldThrowConflict_whenDbConstraintViolated() {
        stubUserAndEvent();
        when(requestRepository.existsByEventIdAndRequesterId(EVENT_ID, USER_ID)).thenReturn(false);
        when(requestRepository.countByEventIdAndStatus(EVENT_ID, RequestStatus.CONFIRMED)).thenReturn(0L);
        when(requestRepository.save(any(ParticipationRequest.class)))
                .thenThrow(new DataIntegrityViolationException("uq_request"));

        assertThrows(ConflictException.class,
                () -> requestService.createRequestByEventId(USER_ID, EVENT_ID));
    }

    @Test
    void cancel_shouldThrowNotFound_whenRequestMissing() {
        when(requestRepository.findByIdAndRequesterId(100L, USER_ID)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class,
                () -> requestService.cancelCurrentUserRequest(USER_ID, 100L));
    }

    @Test
    void cancel_shouldSetCanceledStatus() {
        ParticipationRequest request = createRequest(100L, RequestStatus.PENDING);
        when(requestRepository.findByIdAndRequesterId(100L, USER_ID)).thenReturn(Optional.of(request));
        when(requestRepository.save(request)).thenReturn(request);

        ParticipationRequestDto result = requestService.cancelCurrentUserRequest(USER_ID, 100L);

        assertEquals(RequestStatus.CANCELED, result.status());
    }

    @Test
    void getRequestsForMyEvent_shouldThrowNotFound_whenNotInitiator() {
        when(userRepository.existsById(USER_ID)).thenReturn(true);
        when(eventRepository.findByIdAndInitiatorId(EVENT_ID, USER_ID)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class,
                () -> requestService.getRequestsForMyEvent(USER_ID, EVENT_ID));
    }

    @Test
    void getRequestsForMyEvent_shouldReturnEventRequests() {
        when(userRepository.existsById(INITIATOR_ID)).thenReturn(true);
        when(eventRepository.findByIdAndInitiatorId(EVENT_ID, INITIATOR_ID)).thenReturn(Optional.of(event));
        when(requestRepository.findAllByEventId(EVENT_ID))
                .thenReturn(List.of(createRequest(100L, RequestStatus.PENDING)));

        List<ParticipationRequestDto> result = requestService.getRequestsForMyEvent(INITIATOR_ID, EVENT_ID);

        assertEquals(1, result.size());
    }

    @Test
    void updateStatus_shouldThrowNotFound_whenSomeRequestsMissing() {
        stubInitiatorEvent();
        when(requestRepository.findAllByIdInAndEventId(List.of(100L, 101L), EVENT_ID))
                .thenReturn(List.of(createRequest(100L, RequestStatus.PENDING)));

        assertThrows(NotFoundException.class, () -> requestService.confirmedOrRejected(
                statusUpdate(RequestStatusAction.CONFIRMED, 100L, 101L), INITIATOR_ID, EVENT_ID));
    }

    @Test
    void updateStatus_shouldThrowConflict_whenRequestNotPending() {
        stubInitiatorEvent();
        when(requestRepository.findAllByIdInAndEventId(List.of(100L), EVENT_ID))
                .thenReturn(List.of(createRequest(100L, RequestStatus.CONFIRMED)));

        assertThrows(ConflictException.class, () -> requestService.confirmedOrRejected(
                statusUpdate(RequestStatusAction.REJECTED, 100L), INITIATOR_ID, EVENT_ID));
    }

    @Test
    void updateStatus_shouldRejectAll_andReturnThemInRejectedList() {
        ParticipationRequest r1 = createRequest(100L, RequestStatus.PENDING);
        ParticipationRequest r2 = createRequest(101L, RequestStatus.PENDING);
        stubInitiatorEvent();
        when(requestRepository.findAllByIdInAndEventId(List.of(100L, 101L), EVENT_ID))
                .thenReturn(List.of(r1, r2));

        EventRequestStatusUpdateResult result = requestService.confirmedOrRejected(
                statusUpdate(RequestStatusAction.REJECTED, 100L, 101L), INITIATOR_ID, EVENT_ID);

        assertTrue(result.confirmedRequests().isEmpty());
        assertEquals(2, result.rejectedRequests().size());
        assertEquals(RequestStatus.REJECTED, r1.getStatus());
        assertEquals(RequestStatus.REJECTED, r2.getStatus());
    }

    @Test
    void updateStatus_shouldConfirmAll_whenLimitIsZero() {
        event.setParticipantLimit(0);
        ParticipationRequest r1 = createRequest(100L, RequestStatus.PENDING);
        ParticipationRequest r2 = createRequest(101L, RequestStatus.PENDING);
        stubInitiatorEvent();
        when(requestRepository.findAllByIdInAndEventId(List.of(100L, 101L), EVENT_ID))
                .thenReturn(List.of(r1, r2));

        EventRequestStatusUpdateResult result = requestService.confirmedOrRejected(
                statusUpdate(RequestStatusAction.CONFIRMED, 100L, 101L), INITIATOR_ID, EVENT_ID);

        assertEquals(2, result.confirmedRequests().size());
        assertTrue(result.rejectedRequests().isEmpty());
        verify(requestRepository, never()).countByEventIdAndStatus(any(), any());
    }

    @Test
    void updateStatus_shouldThrowConflict_whenNoFreeSlots() {
        event.setParticipantLimit(1);
        stubInitiatorEvent();
        when(requestRepository.findAllByIdInAndEventId(List.of(100L), EVENT_ID))
                .thenReturn(List.of(createRequest(100L, RequestStatus.PENDING)));
        when(requestRepository.countByEventIdAndStatus(EVENT_ID, RequestStatus.CONFIRMED)).thenReturn(1L);

        assertThrows(ConflictException.class, () -> requestService.confirmedOrRejected(
                statusUpdate(RequestStatusAction.CONFIRMED, 100L), INITIATOR_ID, EVENT_ID));
    }

    @Test
    void updateStatus_shouldConfirmUntilLimit_andRejectTheRest() {
        event.setParticipantLimit(2);
        ParticipationRequest r1 = createRequest(100L, RequestStatus.PENDING);
        ParticipationRequest r2 = createRequest(101L, RequestStatus.PENDING);
        ParticipationRequest otherPending = createRequest(102L, RequestStatus.PENDING);
        stubInitiatorEvent();
        when(requestRepository.findAllByIdInAndEventId(List.of(100L, 101L), EVENT_ID))
                .thenReturn(List.of(r1, r2));
        when(requestRepository.countByEventIdAndStatus(EVENT_ID, RequestStatus.CONFIRMED)).thenReturn(1L);
        when(requestRepository.findAllByEventIdAndStatus(EVENT_ID, RequestStatus.PENDING))
                .thenReturn(List.of(otherPending));

        EventRequestStatusUpdateResult result = requestService.confirmedOrRejected(
                statusUpdate(RequestStatusAction.CONFIRMED, 100L, 101L), INITIATOR_ID, EVENT_ID);

        assertEquals(1, result.confirmedRequests().size());
        assertEquals(100L, result.confirmedRequests().get(0).id());
        assertEquals(2, result.rejectedRequests().size());
        assertEquals(RequestStatus.CONFIRMED, r1.getStatus());
        assertEquals(RequestStatus.REJECTED, r2.getStatus());
        assertEquals(RequestStatus.REJECTED, otherPending.getStatus());
    }

    @Test
    void updateStatus_shouldNotRejectOthers_whenSlotsRemain() {
        event.setParticipantLimit(5);
        ParticipationRequest r1 = createRequest(100L, RequestStatus.PENDING);
        stubInitiatorEvent();
        when(requestRepository.findAllByIdInAndEventId(List.of(100L), EVENT_ID))
                .thenReturn(List.of(r1));
        when(requestRepository.countByEventIdAndStatus(EVENT_ID, RequestStatus.CONFIRMED)).thenReturn(0L);

        EventRequestStatusUpdateResult result = requestService.confirmedOrRejected(
                statusUpdate(RequestStatusAction.CONFIRMED, 100L), INITIATOR_ID, EVENT_ID);

        assertEquals(1, result.confirmedRequests().size());
        assertTrue(result.rejectedRequests().isEmpty());
        verify(requestRepository, never()).findAllByEventIdAndStatus(any(), any());
    }


    private void stubUserAndEvent() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));
    }

    private void stubInitiatorEvent() {
        when(eventRepository.findByIdAndInitiatorId(EVENT_ID, INITIATOR_ID)).thenReturn(Optional.of(event));
    }

    private void stubSave() {
        when(requestRepository.save(any(ParticipationRequest.class)))
                .thenAnswer(invocation -> {
                    ParticipationRequest saved = invocation.getArgument(0);
                    saved.setId(100L);
                    return saved;
                });
    }

    private static EventRequestStatusUpdateRequest statusUpdate(RequestStatusAction action, Long... ids) {
        return new EventRequestStatusUpdateRequest(List.of(ids), action);
    }

    private ParticipationRequest createRequest(Long id, RequestStatus status) {
        ParticipationRequest request = new ParticipationRequest();
        request.setId(id);
        request.setCreated(LocalDateTime.now());
        request.setEvent(event);
        request.setRequester(user);
        request.setStatus(status);
        return request;
    }

    private static User createUser(Long id) {
        User u = new User();
        u.setId(id);
        u.setName("User " + id);
        u.setEmail("user" + id + "@mail.ru");
        return u;
    }

    private static Event createEvent(Long id, User initiator, EventState state,
                                     int participantLimit, boolean requestModeration) {
        Event e = new Event();
        e.setId(id);
        e.setInitiator(initiator);
        e.setState(state);
        e.setParticipantLimit(participantLimit);
        e.setRequestModeration(requestModeration);
        return e;
    }
}
