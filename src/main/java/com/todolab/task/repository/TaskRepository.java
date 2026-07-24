package com.todolab.task.repository;

import com.todolab.task.domain.Task;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TaskRepository extends JpaRepository<Task, Long>, TaskRepositoryCustom {

    @EntityGraph(attributePaths = "ddayGoal")
    List<Task> findByOwnerId(Long ownerId);

    List<Task> findByRecurrenceSeriesIdOrderByOccurrenceDateAscIdAsc(Long recurrenceSeriesId);

    Optional<Task> findByRecurrenceSeriesIdAndOccurrenceDate(Long recurrenceSeriesId, java.time.LocalDate occurrenceDate);

    List<Task> findByRecurrenceSeriesIdAndOwnerIdOrderByOccurrenceDateAscIdAsc(Long recurrenceSeriesId, Long ownerId);

    List<Task> findByRecurrenceSeriesIdAndOwnerIdAndOccurrenceDateGreaterThanEqualOrderByOccurrenceDateAscIdAsc(
            Long recurrenceSeriesId,
            Long ownerId,
            java.time.LocalDate occurrenceDate
    );

    Optional<Task> findByIdAndOwnerId(Long id, Long ownerId);

    boolean existsByIdAndOwnerId(Long id, Long ownerId);
}
