package pj.dooit.goal.repository;

import pj.dooit.goal.domain.GoalVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GoalVersionRepository extends JpaRepository<GoalVersion, Long> {

    Optional<GoalVersion> findByGoalIdAndVersion(Long goalId, int version);
}
