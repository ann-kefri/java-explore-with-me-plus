package ru.practicum.stats.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.practicum.stats.model.EndpointHit;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class EndpointHitRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16.1");

    @Autowired
    private EndpointHitRepository repository;

    private static final LocalDateTime START =
            LocalDateTime.of(2026, 1, 1, 0, 0);

    private static final LocalDateTime END =
            LocalDateTime.of(2026, 1, 2, 0, 0);

    @BeforeEach
    void setUp() {
        saveHit("main", "/events/1", "10.0.0.1", 10);
        saveHit("main", "/events/1", "10.0.0.1", 11);
        saveHit("main", "/events/1", "10.0.0.2", 12);
        saveHit("main", "/events/2", "10.0.0.3", 13);
        saveHit("other", "/events/1", "10.0.0.4", 14);
    }

    @Test
    void givenHits_whenFindStats_thenGroupAndSortByHits() {
        List<StatsProjection> result =
                repository.findStats(START, END);

        assertEquals(3, result.size());

        assertEquals("main", result.getFirst().getApp());
        assertEquals("/events/1", result.getFirst().getUri());
        assertEquals(3L, result.getFirst().getHits());

        assertEquals(2L, result.get(1).getHits()
                         + result.get(2).getHits());
    }

    @Test
    void givenDuplicateIps_whenFindUniqueStats_thenCountDistinctIps() {
        List<StatsProjection> result =
                repository.findUniqueStats(START, END);

        StatsProjection events1 = result.stream()
                .filter(stat -> stat.getApp().equals("main"))
                .filter(stat -> stat.getUri().equals("/events/1"))
                .findFirst()
                .orElseThrow();

        assertEquals(2L, events1.getHits());
    }

    @Test
    void givenUris_whenFindStatsByUris_thenFilterResults() {
        List<StatsProjection> result =
                repository.findStatsByUris(
                        START, END, List.of("/events/2")
                );

        assertEquals(1, result.size());
        assertEquals("main", result.getFirst().getApp());
        assertEquals("/events/2", result.getFirst().getUri());
        assertEquals(1L, result.getFirst().getHits());
    }

    @Test
    void givenUrisAndDuplicateIps_whenFindUniqueStatsByUris_thenCountDistinctIps() {
        List<StatsProjection> result =
                repository.findUniqueStatsByUris(
                        START, END, List.of("/events/1")
                );

        assertEquals(2, result.size());

        StatsProjection main = result.stream()
                .filter(stat -> stat.getApp().equals("main"))
                .findFirst()
                .orElseThrow();

        assertEquals(2L, main.getHits());
    }

    @Test
    void givenHitsOutsidePeriod_whenFindStats_thenExcludeThem() {
        saveHit("main", "/events/3", "10.0.0.5", 48);

        List<StatsProjection> result =
                repository.findStats(START, END);

        assertEquals(3, result.size());
    }

    private void saveHit(String app, String uri, String ip, int hourOffset) {
        EndpointHit hit = new EndpointHit();
        hit.setApp(app);
        hit.setUri(uri);
        hit.setIp(ip);
        hit.setTimestamp(START.plusHours(hourOffset));

        repository.save(hit);
    }
}
