package ru.practicum.event.repository;

import org.springframework.data.jpa.domain.Specification;
import ru.practicum.event.model.Event;
import ru.practicum.event.model.EventState;

import java.time.LocalDateTime;
import java.util.List;

public final class EventSpecifications {

    private EventSpecifications() {
    }

    public static Specification<Event> byUsers(List<Long> users) {
        return (root, query, cb) ->
                users == null || users.isEmpty()
                        ? cb.conjunction()
                        : root.get("initiator").get("id").in(users);
    }

    public static Specification<Event> byStates(List<EventState> states) {
        return (root, query, cb) ->
                states == null || states.isEmpty()
                        ? cb.conjunction()
                        : root.get("state").in(states);
    }

    public static Specification<Event> byCategories(List<Long> categories) {
        return (root, query, cb) ->
                categories == null || categories.isEmpty()
                        ? cb.conjunction()
                        : root.get("category").get("id").in(categories);
    }

    public static Specification<Event> after(LocalDateTime rangeStart) {
        return (root, query, cb) ->
                rangeStart == null
                        ? cb.conjunction()
                        : cb.greaterThanOrEqualTo(
                        root.get("eventDate"), rangeStart
                );
    }

    public static Specification<Event> before(LocalDateTime rangeEnd) {
        return (root, query, cb) ->
                rangeEnd == null
                        ? cb.conjunction()
                        : cb.lessThanOrEqualTo(
                        root.get("eventDate"), rangeEnd
                );
    }

    public static Specification<Event> published() {
        return (root, query, cb) ->
                cb.equal(root.get("state"), EventState.PUBLISHED);
    }

    public static Specification<Event> byText(String text) {
        return (root, query, cb) -> {
            if (text == null || text.isBlank()) {
                return cb.conjunction();
            }

            String pattern = "%" + text.toLowerCase() + "%";

            return cb.or(
                    cb.like(cb.lower(root.get("annotation")), pattern),
                    cb.like(cb.lower(root.get("description")), pattern)
            );
        };
    }

    public static Specification<Event> byPaid(Boolean paid) {
        return (root, query, cb) ->
                paid == null
                        ? cb.conjunction()
                        : cb.equal(root.get("paid"), paid);
    }
}
