package pj.dooit.goal.repository;

import pj.dooit.goal.domain.GoalPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GoalPlanRepository extends JpaRepository<GoalPlan, Long> {

    List<GoalPlan> findByOwnerIdOrderByCreatedAtDescIdDesc(Long ownerId);

    List<GoalPlan> findByOwnerIdAndGoalIdOrderByCreatedAtDescIdDesc(Long ownerId, Long goalId);

    List<GoalPlan> findByOwnerIdAndGoalIsNullOrderByCreatedAtDescIdDesc(Long ownerId);

    Optional<GoalPlan> findByIdAndOwnerId(Long id, Long ownerId);
}
