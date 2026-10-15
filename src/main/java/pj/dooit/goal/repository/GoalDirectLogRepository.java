package pj.dooit.goal.repository;

import pj.dooit.goal.domain.GoalDirectLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface GoalDirectLogRepository extends JpaRepository<GoalDirectLog, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<GoalDirectLog> findByOwnerIdAndGoalIdAndIdempotencyKey(Long ownerId, Long goalId, String key);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<GoalDirectLog> findByIdAndOwnerId(Long id, Long ownerId);
}
