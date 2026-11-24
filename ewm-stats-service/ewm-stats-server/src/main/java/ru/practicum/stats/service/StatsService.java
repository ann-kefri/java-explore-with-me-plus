package ru.practicum.stats.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.stats.exception.InvalidStatsPeriodException;
import ru.practicum.stats.model.EndpointHit;
import ru.practicum.stats.repository.EndpointHitRepository;
import ru.practicum.stats.repository.StatsProjection;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StatsService {

    private final EndpointHitRepository repository;

    @Transactional
    public void saveHit(String app, String uri, String ip,
                        LocalDateTime timestamp) {
        EndpointHit hit = new EndpointHit();
        hit.setApp(app);
        hit.setUri(uri);
        hit.setIp(ip);
        hit.setTimestamp(timestamp);

        repository.save(hit);
    }

    @Transactional(readOnly = true)
    public List<StatsProjection> getStats(
            LocalDateTime start,
            LocalDateTime end,
            List<String> uris,
            boolean unique) {

        if (start == null || end == null || !start.isBefore(end)) {
            throw InvalidStatsPeriodException.invalidRange();
        }

        if (uris == null || uris.isEmpty()) {
            return unique
                    ? repository.findUniqueStats(start, end)
                    : repository.findStats(start, end);
        }

        return unique
                ? repository.findUniqueStatsByUris(start, end, uris)
                : repository.findStatsByUris(start, end, uris);
    }
}
