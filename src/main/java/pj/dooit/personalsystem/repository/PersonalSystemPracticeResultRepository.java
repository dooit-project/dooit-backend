package pj.dooit.personalsystem.repository;

import pj.dooit.personalsystem.domain.PersonalSystemPracticeResult;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface PersonalSystemPracticeResultRepository extends JpaRepository<PersonalSystemPracticeResult, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<PersonalSystemPracticeResult> findLockedByOccurrenceIdIn(List<Long> occurrenceIds);

    @EntityGraph(attributePaths = "occurrence")
    Optional<PersonalSystemPracticeResult> findByOccurrenceId(Long occurrenceId);

    @EntityGraph(attributePaths = "occurrence")
    List<PersonalSystemPracticeResult> findByOccurrenceIdIn(List<Long> occurrenceIds);
}
