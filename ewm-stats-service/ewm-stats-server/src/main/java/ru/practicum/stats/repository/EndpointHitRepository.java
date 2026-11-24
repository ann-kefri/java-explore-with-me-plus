package ru.practicum.stats.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.practicum.stats.model.EndpointHit;

import java.time.LocalDateTime;
import java.util.List;

public interface EndpointHitRepository extends JpaRepository<EndpointHit, Long> {

    @Query("""
            SELECT h.app AS app,
                   h.uri AS uri,
                   COUNT(h) AS hits
            FROM EndpointHit h
            WHERE h.timestamp BETWEEN :start AND :end
            GROUP BY h.app, h.uri
            ORDER BY hits DESC
            """)
    List<StatsProjection> findStats(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    @Query("""
            SELECT h.app AS app,
                   h.uri AS uri,
                   COUNT(DISTINCT h.ip) AS hits
            FROM EndpointHit h
            WHERE h.timestamp BETWEEN :start AND :end
            GROUP BY h.app, h.uri
            ORDER BY hits DESC
            """)
    List<StatsProjection> findUniqueStats(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    @Query("""
            SELECT h.app AS app,
                   h.uri AS uri,
                   COUNT(h) AS hits
            FROM EndpointHit h
            WHERE h.timestamp BETWEEN :start AND :end
              AND h.uri IN :uris
            GROUP BY h.app, h.uri
            ORDER BY hits DESC
            """)
    List<StatsProjection> findStatsByUris(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("uris") List<String> uris
    );

    @Query("""
            SELECT h.app AS app,
                   h.uri AS uri,
                   COUNT(DISTINCT h.ip) AS hits
            FROM EndpointHit h
            WHERE h.timestamp BETWEEN :start AND :end
              AND h.uri IN :uris
            GROUP BY h.app, h.uri
            ORDER BY hits DESC
            """)
    List<StatsProjection> findUniqueStatsByUris(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("uris") List<String> uris
    );
}
