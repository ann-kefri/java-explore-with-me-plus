package ru.practicum.compilation.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.common.OffsetPageRequest;
import ru.practicum.compilation.dto.CompilationDto;
import ru.practicum.compilation.dto.NewCompilationDto;
import ru.practicum.compilation.dto.UpdateCompilationRequest;
import ru.practicum.compilation.mapper.CompilationMapper;
import ru.practicum.compilation.model.Compilation;
import ru.practicum.compilation.repository.CompilationRepository;
import ru.practicum.event.model.Event;
import ru.practicum.event.repository.EventRepository;
import ru.practicum.exception.ConflictException;
import ru.practicum.exception.NotFoundException;
import ru.practicum.request.model.RequestStatus;
import ru.practicum.request.repository.ConfirmedRequestCount;
import ru.practicum.request.repository.ParticipationRequestRepository;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CompilationService {

    private final CompilationRepository compilationRepository;
    private final EventRepository eventRepository;
    private final ParticipationRequestRepository requestRepository;
    private final CompilationMapper compilationMapper;

    @Transactional
    public CompilationDto create(NewCompilationDto dto) {
        if (compilationRepository.existsByTitle(dto.title())) {
            throw new ConflictException("Compilation with title " + dto.title() + " already exists");
        }
        Compilation compilation = new Compilation();
        compilation.setTitle(dto.title());
        compilation.setPinned(dto.pinned());
        compilation.setEvents(resolveEvents(dto.events()));

        Compilation saved = compilationRepository.save(compilation);
        return compilationMapper.toCompilationDto(saved, getConfirmedCounts(saved.getEvents()));
    }

    @Transactional
    public CompilationDto update(Long compId, UpdateCompilationRequest request) {
        Compilation compilation = getCompilationOrThrow(compId);

        if (request.title() != null) {
            compilation.setTitle(request.title());
        }
        if (request.pinned() != null) {
            compilation.setPinned(request.pinned());
        }
        if (request.events() != null) {
            compilation.setEvents(resolveEvents(request.events()));
        }
        return compilationMapper.toCompilationDto(compilation, getConfirmedCounts(compilation.getEvents()));
    }

    @Transactional
    public void delete(Long compId) {
        compilationRepository.delete(getCompilationOrThrow(compId));
    }

    public List<CompilationDto> getAll(Boolean pinned, int from, int size) {
        Pageable pageable = new OffsetPageRequest(from, size, Sort.unsorted());
        List<Long> ids = compilationRepository.findIdsByPinned(pinned, pageable);
        if (ids.isEmpty()) {
            return List.of();
        }
        return compilationRepository.findAllWithEventsByIds(ids).stream()
                .map(c -> compilationMapper.toCompilationDto(c, getConfirmedCounts(c.getEvents())))
                .toList();
    }

    public CompilationDto getById(Long compId) {
        Compilation compilation = getCompilationOrThrow(compId);
        return compilationMapper.toCompilationDto(compilation, getConfirmedCounts(compilation.getEvents()));
    }

    private Compilation getCompilationOrThrow(Long compId) {
        return compilationRepository.findByIdWithEvents(compId)
                .orElseThrow(() -> new NotFoundException("Compilation with id=" + compId + " was not found"));
    }

    private Set<Event> resolveEvents(Set<Long> eventIds) {
        if (eventIds == null || eventIds.isEmpty()) {
            return new HashSet<>();
        }
        return new HashSet<>(eventRepository.findAllByIdWithDetails(eventIds));
    }

    private Map<Long, Long> getConfirmedCounts(Set<Event> events) {
        if (events.isEmpty()) {
            return Map.of();
        }
        List<Long> eventIds = events.stream()
                .map(Event::getId)
                .toList();

        return requestRepository.countByEventIdsAndStatus(eventIds, RequestStatus.CONFIRMED).stream()
                .collect(Collectors.toMap(
                        ConfirmedRequestCount::getEventId,
                        ConfirmedRequestCount::getCount
                ));
    }
}
