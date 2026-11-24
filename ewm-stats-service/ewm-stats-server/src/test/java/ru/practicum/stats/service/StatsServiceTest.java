package ru.practicum.stats.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.practicum.stats.exception.InvalidStatsPeriodException;
import ru.practicum.stats.model.EndpointHit;
import ru.practicum.stats.repository.EndpointHitRepository;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class StatsServiceTest {

    @Mock
    private EndpointHitRepository repository;

    private StatsService service;

    private final LocalDateTime start =
            LocalDateTime.of(2026, 1, 1, 0, 0);

    private final LocalDateTime end =
            LocalDateTime.of(2026, 1, 2, 0, 0);

    @BeforeEach
    void setUp() {
        service = new StatsService(repository);
    }

    @Test
    void givenHit_whenSaveHit_thenPersistHit() {
        LocalDateTime timestamp =
                LocalDateTime.of(2026, 1, 1, 12, 0);

        service.saveHit(
                "ewm-main-service",
                "/events/1",
                "192.168.1.10",
                timestamp
        );

        ArgumentCaptor<EndpointHit> captor =
                ArgumentCaptor.forClass(EndpointHit.class);

        verify(repository).save(captor.capture());

        EndpointHit saved = captor.getValue();

        assertEquals("ewm-main-service", saved.getApp());
        assertEquals("/events/1", saved.getUri());
        assertEquals("192.168.1.10", saved.getIp());
        assertEquals(timestamp, saved.getTimestamp());
    }

    @Test
    void givenNoUrisAndNonUnique_whenGetStats_thenUseRegularQuery() {
        service.getStats(start, end, null, false);

        verify(repository).findStats(start, end);
        verifyNoMoreInteractions(repository);
    }

    @Test
    void givenNoUrisAndUnique_whenGetStats_thenUseUniqueQuery() {
        service.getStats(start, end, null, true);

        verify(repository).findUniqueStats(start, end);
        verifyNoMoreInteractions(repository);
    }

    @Test
    void givenUrisAndNonUnique_whenGetStats_thenUseFilteredQuery() {
        List<String> uris = List.of("/events/1");

        service.getStats(start, end, uris, false);

        verify(repository).findStatsByUris(start, end, uris);
        verifyNoMoreInteractions(repository);
    }

    @Test
    void givenUrisAndUnique_whenGetStats_thenUseFilteredUniqueQuery() {
        List<String> uris = List.of("/events/1");

        service.getStats(start, end, uris, true);

        verify(repository).findUniqueStatsByUris(start, end, uris);
        verifyNoMoreInteractions(repository);
    }

    @Test
    void givenEmptyUris_whenGetStats_thenUseUnfilteredQuery() {
        service.getStats(start, end, List.of(), false);

        verify(repository).findStats(start, end);
        verifyNoMoreInteractions(repository);
    }

    @Test
    void givenReversedDates_whenGetStats_thenThrowInvalidStatsPeriodException() {
        assertThrows(
                InvalidStatsPeriodException.class,
                () -> service.getStats(end, start, null, false)
        );

        verifyNoInteractions(repository);
    }

    @Test
    void givenEqualDates_whenGetStats_thenThrowInvalidStatsPeriodException() {
        assertThrows(
                InvalidStatsPeriodException.class,
                () -> service.getStats(start, start, null, false)
        );

        verifyNoInteractions(repository);
    }

    @Test
    void givenNullStart_whenGetStats_thenThrowInvalidStatsPeriodException() {
        assertThrows(
                InvalidStatsPeriodException.class,
                () -> service.getStats(null, end, null, false)
        );

        verifyNoInteractions(repository);
    }
}
