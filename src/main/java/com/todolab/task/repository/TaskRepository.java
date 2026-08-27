package com.todolab.task.repository;

import com.todolab.common.domain.ResourceScope;
import com.todolab.task.domain.Task;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
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

    @EntityGraph(attributePaths = {"ddayGoal", "recurrenceSeries"})
    @Query("""
            select task
            from Task task
            where task.owner.id = :ownerId
              and task.scope = :scope
              and task.startAt is not null
              and task.completedAt is null
              and (task.recurrenceException is null or task.recurrenceException <> com.todolab.task.domain.RecurrenceExceptionType.SKIPPED)
              and task.startAt < :toExclusive
              and (task.endAt is null or task.endAt > :fromInclusive)
            order by task.startAt asc, task.id asc
            """)
    List<Task> findCalendarFeedTasks(
            @Param("ownerId") Long ownerId,
            @Param("scope") ResourceScope scope,
            @Param("fromInclusive") LocalDateTime fromInclusive,
            @Param("toExclusive") LocalDateTime toExclusive
    );

    default boolean existsByIdAndOwnerId(Long id, Long ownerId) {
        return existsByIdAndOwnerIdAndScope(id, ownerId, ResourceScope.PERSONAL);
    }

    boolean existsByIdAndOwnerIdAndScope(Long id, Long ownerId, ResourceScope scope);
}
