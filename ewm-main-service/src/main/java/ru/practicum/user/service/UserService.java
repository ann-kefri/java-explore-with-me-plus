package ru.practicum.user.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.common.OffsetPageRequest;
import ru.practicum.exception.ConflictException;
import ru.practicum.exception.NotFoundException;
import ru.practicum.user.dto.NewUserRequest;
import ru.practicum.user.dto.UserDto;
import ru.practicum.user.mapper.UserMapper;
import ru.practicum.user.model.User;
import ru.practicum.user.repository.UserRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    @Transactional
    public UserDto add(NewUserRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new ConflictException("User with email " + request.email() + " already exists");
        }
        User user = new User();
        user.setEmail(request.email());
        user.setName(request.name());
        return userMapper.toUserDto(userRepository.save(user));
    }

    public List<UserDto> getAll(List<Long> ids, int from, int size) {
        Pageable pageable = new OffsetPageRequest(from, size, Sort.unsorted());

        List<User> users = (ids == null || ids.isEmpty())
                ? userRepository.findAll(pageable).getContent()
                : userRepository.findAllByIdIn(ids, pageable);

        return users.stream()
                .map(userMapper::toUserDto)
                .toList();
    }

    @Transactional
    public void delete(Long userId) {
        userRepository.delete(getUserOrThrow(userId));
    }

    private User getUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User with id=" + userId + " was not found"));
    }
}
