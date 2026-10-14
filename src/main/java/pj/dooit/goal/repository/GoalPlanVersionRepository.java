package pj.dooit.goal.repository;

import pj.dooit.goal.domain.GoalPlanVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GoalPlanVersionRepository extends JpaRepository<GoalPlanVersion, Long> {

    Optional<GoalPlanVersion> findByPlanIdAndVersion(Long planId, int version);
}
