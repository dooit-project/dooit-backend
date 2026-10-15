package pj.dooit.goal.repository;

import pj.dooit.goal.domain.GoalPlanVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;
import java.time.LocalDate;

public interface GoalPlanVersionRepository extends JpaRepository<GoalPlanVersion, Long> {

    List<GoalPlanVersion> findByPlanIdInAndEffectiveFromDateLessThanEqualOrderByVersionAsc(List<Long> planIds, LocalDate to);

    Optional<GoalPlanVersion> findByPlanIdAndVersion(Long planId, int version);
    List<GoalPlanVersion> findByPlanIdAndEffectiveFromDateLessThanEqualOrderByVersionAsc(Long id, LocalDate date);
}
