package pj.dooit.task.repository;

import pj.dooit.common.domain.ResourceScope;
import pj.dooit.task.domain.RecurrenceSeries;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RecurrenceSeriesRepository extends JpaRepository<RecurrenceSeries, Long> {

    default List<RecurrenceSeries> findByOwnerId(Long ownerId) {
        return findByOwnerIdAndScope(ownerId, ResourceScope.PERSONAL);
    }

    List<RecurrenceSeries> findByOwnerIdAndScope(Long ownerId, ResourceScope scope);

    default List<RecurrenceSeries> findByWorkspaceId(Long workspaceId) {
        return findByWorkspaceIdAndScope(workspaceId, ResourceScope.WORKSPACE);
    }

    List<RecurrenceSeries> findByWorkspaceIdAndScope(Long workspaceId, ResourceScope scope);

    default Optional<RecurrenceSeries> findByIdAndOwnerId(Long id, Long ownerId) {
        return findByIdAndOwnerIdAndScope(id, ownerId, ResourceScope.PERSONAL);
    }

    Optional<RecurrenceSeries> findByIdAndOwnerIdAndScope(Long id, Long ownerId, ResourceScope scope);
}
