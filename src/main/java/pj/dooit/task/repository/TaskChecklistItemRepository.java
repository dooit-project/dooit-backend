package pj.dooit.task.repository;

import pj.dooit.task.domain.TaskChecklistItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TaskChecklistItemRepository extends JpaRepository<TaskChecklistItem, Long> {

    int countByTaskId(Long taskId);

    List<TaskChecklistItem> findAllByTaskIdOrderBySortOrderAscIdAsc(Long taskId);

    Optional<TaskChecklistItem> findByIdAndTaskId(Long id, Long taskId);

    @Query("select coalesce(max(item.sortOrder), -1) from TaskChecklistItem item where item.task.id = :taskId")
    int findMaxSortOrder(@Param("taskId") Long taskId);

    @Modifying
    @Query("delete from TaskChecklistItem item where item.task.id = :taskId")
    void deleteAllByTaskId(@Param("taskId") Long taskId);
}
