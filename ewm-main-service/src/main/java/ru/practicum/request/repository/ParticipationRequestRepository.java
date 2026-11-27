package ru.practicum.request.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.practicum.request.dto.ConfirmedCount;
import ru.practicum.request.model.ParticipationRequest;
import ru.practicum.request.model.RequestStatus;

import java.util.Collection;
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
            select new ru.pracricum.request.dto.ConfirmedCount(r.event.id, count(r))
            from ParticipationRequest r
            where r.event.id in :eventsIds and r.status = :status
            group by r.event.id
""")
    List<ConfirmedCount> countConfirmedByEventIds(@Param("eventIds")Collection<Long> eventIds,
                                                  @Param("status") RequestStatus status);


}
