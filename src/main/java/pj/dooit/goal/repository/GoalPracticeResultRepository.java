package pj.dooit.goal.repository;

import pj.dooit.goal.domain.GoalPracticeResult;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.time.LocalDateTime;

import java.util.List;
import java.util.Optional;

public interface GoalPracticeResultRepository extends JpaRepository<GoalPracticeResult, Long> {

    @EntityGraph(attributePaths = "occurrence")
    @Query("""
            select r from GoalPracticeResult r
            where r.occurrence.owner.id = :ownerId and r.occurrence.goalId = :goalId
              and r.occurrence.status = pj.dooit.goal.domain.GoalOccurrenceStatus.PRACTICED
              and r.practicedAt >= :from and r.practicedAt < :toExclusive
            """)
    List<GoalPracticeResult> findJourneyResults(Long ownerId, Long goalId, LocalDateTime from, LocalDateTime toExclusive);

    @EntityGraph(attributePaths = "occurrence")
    Optional<GoalPracticeResult> findByOccurrenceId(Long occurrenceId);

    @EntityGraph(attributePaths = "occurrence")
    List<GoalPracticeResult> findByOccurrenceIdIn(List<Long> occurrenceIds);
}
