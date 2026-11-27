package ru.practicum.request.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.practicum.request.model.ParticipationRequest;
import ru.practicum.request.model.RequestStatus;

import java.util.List;
import java.util.Optional;

public interface ParticipationRequestRepository extends JpaRepository<ParticipationRequest, Long> {
    Optional<ParticipationRequest> findByIdAndRequesterId(Long id, Long requesterId);

    List<ParticipationRequest> findAllByRequesterId(Long requesterId);

    List<ParticipationRequest> findAllByEventId(Long eventId);

    boolean existsByEventIdAndRequesterId(Long eventId, Long requesterId);

    long countByEventIdAndStatus(Long eventId, RequestStatus status);

    List<ParticipationRequest> findAllByIdInAndEventId(List<Long> ids, Long eventId);

    List<ParticipationRequest> findAllByEventIdAndStatus(Long eventId, RequestStatus status);

    @Query("""
        SELECT r.event.id AS eventId, COUNT(r) AS count
        FROM ParticipationRequest r
        WHERE r.event.id IN :eventIds
          AND r.status = :status
        GROUP BY r.event.id
        """)
    List<ConfirmedRequestCount> countByEventIdsAndStatus(
            @Param("eventIds") List<Long> eventIds,
            @Param("status") RequestStatus status
    );
}
