package pj.dooit.goal.repository;

import pj.dooit.goal.domain.GoalDirectLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.List;
import java.time.LocalDate;

public interface GoalDirectLogRepository extends JpaRepository<GoalDirectLog, Long> {
    List<GoalDirectLog> findByOwnerIdAndGoalIdAndCanceledAtIsNullAndPracticedDateBetween(
            Long ownerId, Long goalId, LocalDate from, LocalDate to);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<GoalDirectLog> findByOwnerIdAndGoalIdAndIdempotencyKey(Long ownerId, Long goalId, String key);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<GoalDirectLog> findByIdAndOwnerId(Long id, Long ownerId);
}
