package ru.practicum.category.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.category.dto.CategoryDto;
import ru.practicum.category.dto.NewCategoryDto;
import ru.practicum.category.mapper.CategoryMapper;
import ru.practicum.category.model.Category;
import ru.practicum.category.repository.CategoryRepository;
import ru.practicum.common.OffsetPageRequest;
import ru.practicum.event.repository.EventRepository;
import ru.practicum.exception.ConflictException;
import ru.practicum.exception.NotFoundException;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final EventRepository eventRepository;
    private final CategoryMapper categoryMapper;

    @Transactional
    public CategoryDto add(NewCategoryDto dto) {
        if (categoryRepository.existsByName(dto.name())) {
            throw new ConflictException("Category with name " + dto.name() + " already exists");
        }

        Category category = new Category();
        category.setName(dto.name());
        return categoryMapper.toCategoryDto(categoryRepository.save(category));
    }

    @Transactional
    public CategoryDto update(Long catId, CategoryDto dto) {
        Category category = getCategoryOrThrow(catId);

        categoryRepository.findByName(dto.name())
                .filter(existing -> !existing.getId().equals(catId))
                .ifPresent(existing -> {
                    throw new ConflictException("Category with name " + dto.name() + " already exists");
                });

        category.setName(dto.name());
        return categoryMapper.toCategoryDto(category);
    }

    @Transactional
    public void delete(Long catId) {
        Category category = getCategoryOrThrow(catId);

        if (eventRepository.existsByCategoryId(catId)) {
            throw new ConflictException("The category is not empty");
        }

        categoryRepository.delete(category);
    }

    public List<CategoryDto> getAll(int from, int size) {
        Pageable pageable = new OffsetPageRequest(from, size, Sort.unsorted());
        return categoryRepository.findAll(pageable)
                .map(categoryMapper::toCategoryDto)
                .getContent();
    }

    public CategoryDto getById(Long catId) {
        return categoryMapper.toCategoryDto(getCategoryOrThrow(catId));
    }


    private Category getCategoryOrThrow(Long catId) {
        return categoryRepository.findById(catId)
                .orElseThrow(() -> new NotFoundException("Category with id=" + catId + " was not found"));
    }
}
