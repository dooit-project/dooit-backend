package pj.dooit.goal.repository;

import pj.dooit.goal.domain.GoalPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface GoalPlanRepository extends JpaRepository<GoalPlan, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from GoalPlan p where p.owner.id = :ownerId order by p.id")
    List<GoalPlan> findAllForMaterialization(Long ownerId);

    List<GoalPlan> findByOwnerIdOrderByCreatedAtDescIdDesc(Long ownerId);

    List<GoalPlan> findByOwnerIdAndGoalIdOrderByCreatedAtDescIdDesc(Long ownerId, Long goalId);

    List<GoalPlan> findByOwnerIdAndGoalIsNullOrderByCreatedAtDescIdDesc(Long ownerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from GoalPlan p where p.id = :id and p.owner.id = :ownerId")
    Optional<GoalPlan> findForUpdate(Long id, Long ownerId);

    Optional<GoalPlan> findByIdAndOwnerId(Long id, Long ownerId);
}
