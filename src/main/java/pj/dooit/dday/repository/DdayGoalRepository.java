package pj.dooit.dday.repository;

import pj.dooit.common.domain.ResourceScope;
import pj.dooit.dday.domain.DdayGoal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DdayGoalRepository extends JpaRepository<DdayGoal, Long> {

    default List<DdayGoal> findAllByOrderByTargetDateAscIdAsc() {
        return findAllByScopeOrderByTargetDateAscIdAsc(ResourceScope.PERSONAL);
    }

    List<DdayGoal> findAllByScopeOrderByTargetDateAscIdAsc(ResourceScope scope);

    default List<DdayGoal> findAllByOwnerIdOrderByTargetDateAscIdAsc(Long ownerId) {
        return findAllByOwnerIdAndScopeOrderByTargetDateAscIdAsc(ownerId, ResourceScope.PERSONAL);
    }

    List<DdayGoal> findAllByOwnerIdAndScopeOrderByTargetDateAscIdAsc(Long ownerId, ResourceScope scope);

    default List<DdayGoal> findByTargetDateBetweenOrderByTargetDateAscIdAsc(LocalDate startDate, LocalDate endDate) {
        return findByScopeAndTargetDateBetweenOrderByTargetDateAscIdAsc(ResourceScope.PERSONAL, startDate, endDate);
    }

    List<DdayGoal> findByScopeAndTargetDateBetweenOrderByTargetDateAscIdAsc(
            ResourceScope scope,
            LocalDate startDate,
            LocalDate endDate
    );

    default List<DdayGoal> findByOwnerIdAndTargetDateBetweenOrderByTargetDateAscIdAsc(
            Long ownerId,
            LocalDate startDate,
            LocalDate endDate
    ) {
        return findByOwnerIdAndScopeAndTargetDateBetweenOrderByTargetDateAscIdAsc(
                ownerId,
                ResourceScope.PERSONAL,
                startDate,
                endDate
        );
    }

    List<DdayGoal> findByOwnerIdAndScopeAndTargetDateBetweenOrderByTargetDateAscIdAsc(
            Long ownerId,
            ResourceScope scope,
            LocalDate startDate,
            LocalDate endDate
    );

    default Optional<DdayGoal> findByIdAndOwnerId(Long id, Long ownerId) {
        return findByIdAndOwnerIdAndScope(id, ownerId, ResourceScope.PERSONAL);
    }

    Optional<DdayGoal> findByIdAndOwnerIdAndScope(Long id, Long ownerId, ResourceScope scope);

    Optional<DdayGoal> findByIdAndWorkspaceIdAndScope(Long id, Long workspaceId, ResourceScope scope);

    List<DdayGoal> findAllByWorkspaceIdAndScopeOrderByTargetDateAscIdAsc(Long workspaceId, ResourceScope scope);

    default boolean existsByIdAndOwnerId(Long id, Long ownerId) {
        return existsByIdAndOwnerIdAndScope(id, ownerId, ResourceScope.PERSONAL);
    }

    boolean existsByIdAndOwnerIdAndScope(Long id, Long ownerId, ResourceScope scope);
}
