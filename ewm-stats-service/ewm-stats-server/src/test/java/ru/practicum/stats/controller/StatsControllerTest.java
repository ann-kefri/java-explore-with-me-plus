package ru.practicum.stats.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.practicum.stats.repository.StatsProjection;
import ru.practicum.stats.service.StatsService;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(StatsController.class)
class StatsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private StatsService statsService;

    @Test
    void givenValidHit_whenPostHit_thenReturnsCreated() throws Exception {
        mockMvc.perform(post("/hit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\n" +
                                 "  \"app\": \"ewm-main-service\",\n" +
                                 "  \"uri\": \"/events/1\",\n" +
                                 "  \"ip\": \"192.168.1.1\",\n" +
                                 "  \"timestamp\": \"2026-11-23 12:00:00\"\n" +
                                 "}\n"))
                .andExpect(status().isCreated());

        verify(statsService).saveHit(
                "ewm-main-service",
                "/events/1",
                "192.168.1.1",
                LocalDateTime.of(2026, 11, 23, 12, 0)
        );
    }

    @Test
    void givenInvalidHit_whenPostHit_thenReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/hit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\n" +
                                 "  \"app\": \"\",\n" +
                                 "  \"uri\": \"/events/1\",\n" +
                                 "  \"ip\": \"192.168.1.1\"\n" +
                                 "}\n"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(statsService);
    }

    @Test
    void givenValidParameters_whenGetStats_thenReturnsStatistics() throws Exception {
        StatsProjection projection = mock(StatsProjection.class);

        when(projection.getApp()).thenReturn("ewm-main-service");
        when(projection.getUri()).thenReturn("/events/1");
        when(projection.getHits()).thenReturn(5L);

        when(statsService.getStats(
                any(LocalDateTime.class),
                any(LocalDateTime.class),
                eq(List.of("/events/1")),
                eq(true)
        )).thenReturn(List.of(projection));

        mockMvc.perform(get("/stats")
                        .param("start", "2026-11-01 00:00:00")
                        .param("end", "2026-11-23 23:59:59")
                        .param("uris", "/events/1")
                        .param("unique", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].app").value("ewm-main-service"))
                .andExpect(jsonPath("$[0].uri").value("/events/1"))
                .andExpect(jsonPath("$[0].hits").value(5));

        verify(statsService).getStats(
                LocalDateTime.of(2026, 11, 1, 0, 0),
                LocalDateTime.of(2026, 11, 23, 23, 59, 59),
                List.of("/events/1"),
                true
        );
    }

    @Test
    void givenMissingOptionalParameters_whenGetStats_thenUsesDefaults()
            throws Exception {

        when(statsService.getStats(
                any(LocalDateTime.class),
                any(LocalDateTime.class),
                isNull(),
                eq(false)
        )).thenReturn(List.of());

        mockMvc.perform(get("/stats")
                        .param("start", "2026-11-01 00:00:00")
                        .param("end", "2026-11-23 23:59:59"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));

        verify(statsService).getStats(
                any(LocalDateTime.class),
                any(LocalDateTime.class),
                isNull(),
                eq(false)
        );
    }

    @Test
    void givenInvalidDateFormat_whenGetStats_thenReturnsBadRequest()
            throws Exception {

        mockMvc.perform(get("/stats")
                        .param("start", "invalid-date")
                        .param("end", "2026-11-23 23:59:59"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(statsService);
    }

    @Test
    void givenInvalidPeriod_whenGetStats_thenReturnsBadRequest()
            throws Exception {

        when(statsService.getStats(
                any(LocalDateTime.class),
                any(LocalDateTime.class),
                any(),
                eq(false)
        )).thenThrow(
                ru.practicum.stats.exception.InvalidStatsPeriodException
                        .invalidRange()
        );

        mockMvc.perform(get("/stats")
                        .param("start", "2026-11-23 23:59:59")
                        .param("end", "2026-11-01 00:00:00"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail")
                        .value("Start must be before end"));
    }
}
