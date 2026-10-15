package pj.dooit.goal.repository;

import pj.dooit.goal.domain.GoalPracticeResult;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GoalPracticeResultRepository extends JpaRepository<GoalPracticeResult, Long> {

    @EntityGraph(attributePaths = "occurrence")
    Optional<GoalPracticeResult> findByOccurrenceId(Long occurrenceId);

    @EntityGraph(attributePaths = "occurrence")
    List<GoalPracticeResult> findByOccurrenceIdIn(List<Long> occurrenceIds);
}
