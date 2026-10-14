package pj.dooit.goal.repository;

import pj.dooit.goal.domain.GoalPlanOccurrence;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;

public interface GoalPlanOccurrenceRepository extends JpaRepository<GoalPlanOccurrence, Long> {
    List<GoalPlanOccurrence> findByPlanIdAndServiceDateBetweenOrderByServiceDateAscIdAsc(
            Long planId, LocalDate from, LocalDate to);
}
