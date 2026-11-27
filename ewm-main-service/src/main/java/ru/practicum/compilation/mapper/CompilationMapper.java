package ru.practicum.compilation.mapper;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.practicum.compilation.dto.CompilationDto;
import ru.practicum.compilation.model.Compilation;
import ru.practicum.event.dto.EventShortDto;
import ru.practicum.event.mapper.EventMapper;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class CompilationMapper {

    private final EventMapper eventMapper;

    public CompilationDto toCompilationDto(Compilation compilation, Map<Long, Long> confirmedCounts) {
        Set<EventShortDto> eventDtos = compilation.getEvents().stream()
                .map(event -> eventMapper.toShortDto(
                        event,
                        confirmedCounts.getOrDefault(event.getId(), 0L),
                        0L
                ))
                .collect(Collectors.toCollection(LinkedHashSet::new));

        return new CompilationDto(
                compilation.getId(),
                eventDtos,
                compilation.isPinned(),
                compilation.getTitle()
        );
    }
}
