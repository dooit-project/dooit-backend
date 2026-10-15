package pj.dooit.goal.repository;

import pj.dooit.goal.domain.GoalPlanOccurrence;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.EntityGraph;

public interface GoalPlanOccurrenceRepository extends JpaRepository<GoalPlanOccurrence, Long> {
    @EntityGraph(attributePaths = "plan")
    List<GoalPlanOccurrence> findByOwnerIdAndGoalIdAndServiceDateBetween(Long ownerId, Long goalId, LocalDate from, LocalDate to);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<GoalPlanOccurrence> findByPlanIdAndServiceDateBetweenOrderByServiceDateAscIdAsc(
            Long planId, LocalDate from, LocalDate to);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<GoalPlanOccurrence> findByPlanIdOrderByServiceDateAscIdAsc(Long planId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<GoalPlanOccurrence> findByIdAndOwnerId(Long id, Long ownerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<GoalPlanOccurrence> findByLinkedTaskId(Long taskId);
}
