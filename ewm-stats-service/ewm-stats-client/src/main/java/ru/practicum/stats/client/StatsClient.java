package ru.practicum.stats.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.DefaultUriBuilderFactory;
import ru.practicum.stats.dto.EndpointHit;
import ru.practicum.stats.dto.ViewStats;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class StatsClient {
    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final RestTemplate restTemplate;

    public StatsClient(@Value("${stats-server.url}") String serverUrl, RestTemplateBuilder builder) {
        this.restTemplate = builder.uriTemplateHandler(new DefaultUriBuilderFactory(serverUrl)).build();
    }

    public void hit(EndpointHit dto) {
        restTemplate.postForEntity("/hit", dto, Void.class);
    }

    public List<ViewStats> getStats(LocalDateTime start, LocalDateTime end, List<String> uris, boolean unique) {
        String path = "/stats?start={start}&end={end}&unique={unique}";

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("start", start.format(FORMATTER));
        parameters.put("end", end.format(FORMATTER));
        parameters.put("unique", unique);
        if (uris != null && !uris.isEmpty()) {
            path += "&uris={uris}";
            parameters.put("uris", String.join(",", uris));
        }
        ViewStats[] stats = restTemplate.getForObject(path, ViewStats[].class, parameters);

        return stats == null ? List.of() : List.of(stats);
    }

}
