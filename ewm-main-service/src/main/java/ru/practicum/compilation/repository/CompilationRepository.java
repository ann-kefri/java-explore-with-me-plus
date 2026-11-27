package ru.practicum.compilation.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.practicum.compilation.model.Compilation;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CompilationRepository extends JpaRepository<Compilation, Long> {

    @Query("SELECT DISTINCT c FROM Compilation c "
            + "LEFT JOIN FETCH c.events e "
            + "LEFT JOIN FETCH e.category "
            + "LEFT JOIN FETCH e.initiator "
            + "WHERE c.id = :id")
    Optional<Compilation> findByIdWithEvents(@Param("id") Long id);

    @Query("SELECT c.id FROM Compilation c WHERE (:pinned IS NULL OR c.pinned = :pinned)")
    List<Long> findIdsByPinned(@Param("pinned") Boolean pinned, Pageable pageable);

    @Query("SELECT DISTINCT c FROM Compilation c "
            + "LEFT JOIN FETCH c.events e "
            + "LEFT JOIN FETCH e.category "
            + "LEFT JOIN FETCH e.initiator "
            + "WHERE c.id IN :ids")
    List<Compilation> findAllWithEventsByIds(@Param("ids") Collection<Long> ids);


    boolean existsByTitle(String title);


}
