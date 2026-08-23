package com.todolab.task.repository;

import com.todolab.common.domain.ResourceScope;
import com.todolab.task.domain.Task;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TaskRepository extends JpaRepository<Task, Long>, TaskRepositoryCustom {

    @EntityGraph(attributePaths = "ddayGoal")
    default List<Task> findByOwnerId(Long ownerId) {
        return findByOwnerIdAndScope(ownerId, ResourceScope.PERSONAL);
    }

    @EntityGraph(attributePaths = "ddayGoal")
    List<Task> findByOwnerIdAndScope(Long ownerId, ResourceScope scope);

    List<Task> findByRecurrenceSeriesIdOrderByOccurrenceDateAscIdAsc(Long recurrenceSeriesId);

    Optional<Task> findByRecurrenceSeriesIdAndOccurrenceDate(Long recurrenceSeriesId, java.time.LocalDate occurrenceDate);

    List<Task> findByRecurrenceSeriesIdAndOwnerIdOrderByOccurrenceDateAscIdAsc(Long recurrenceSeriesId, Long ownerId);

    List<Task> findByRecurrenceSeriesIdAndOwnerIdAndOccurrenceDateGreaterThanEqualOrderByOccurrenceDateAscIdAsc(
            Long recurrenceSeriesId,
            Long ownerId,
            java.time.LocalDate occurrenceDate
    );

    List<Task> findByRecurrenceSeriesIdAndWorkspaceIdAndScopeOrderByOccurrenceDateAscIdAsc(
            Long recurrenceSeriesId,
            Long workspaceId,
            ResourceScope scope
    );

    List<Task> findByRecurrenceSeriesIdAndWorkspaceIdAndScopeAndOccurrenceDateGreaterThanEqualOrderByOccurrenceDateAscIdAsc(
            Long recurrenceSeriesId,
            Long workspaceId,
            ResourceScope scope,
            java.time.LocalDate occurrenceDate
    );

    default Optional<Task> findByIdAndOwnerId(Long id, Long ownerId) {
        return findByIdAndOwnerIdAndScope(id, ownerId, ResourceScope.PERSONAL);
    }

    Optional<Task> findByIdAndOwnerIdAndScope(Long id, Long ownerId, ResourceScope scope);

    Optional<Task> findByIdAndWorkspaceIdAndScope(Long id, Long workspaceId, ResourceScope scope);

    List<Task> findByWorkspaceIdAndScope(Long workspaceId, ResourceScope scope);

    default boolean existsByIdAndOwnerId(Long id, Long ownerId) {
        return existsByIdAndOwnerIdAndScope(id, ownerId, ResourceScope.PERSONAL);
    }

    boolean existsByIdAndOwnerIdAndScope(Long id, Long ownerId, ResourceScope scope);
}
