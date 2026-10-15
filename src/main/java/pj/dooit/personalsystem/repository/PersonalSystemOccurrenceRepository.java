package pj.dooit.personalsystem.repository;

import pj.dooit.personalsystem.domain.PersonalSystemOccurrence;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

public interface PersonalSystemOccurrenceRepository extends JpaRepository<PersonalSystemOccurrence, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<PersonalSystemOccurrence> findBySystemIdOrderByIdAsc(Long systemId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PersonalSystemOccurrence> findByLinkedTaskId(Long linkedTaskId);


    @EntityGraph(attributePaths = {"system", "linkedTask"})
    List<PersonalSystemOccurrence> findByOwnerIdAndServiceDateBetweenOrderByServiceDateAscIdAsc(
            Long ownerId,
            LocalDate from,
            LocalDate to
    );

    @EntityGraph(attributePaths = {"system", "linkedTask"})
    List<PersonalSystemOccurrence> findBySystemIdAndOwnerIdAndServiceDateBetweenOrderByServiceDateAscIdAsc(
            Long systemId,
            Long ownerId,
            LocalDate from,
            LocalDate to
    );

    Optional<PersonalSystemOccurrence> findBySystemIdAndOwnerIdAndServiceDate(Long systemId, Long ownerId, LocalDate serviceDate);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PersonalSystemOccurrence> findLockedByIdAndOwnerId(Long id, Long ownerId);

    @EntityGraph(attributePaths = {"system", "linkedTask"})
    Optional<PersonalSystemOccurrence> findByIdAndOwnerId(Long id, Long ownerId);

    @EntityGraph(attributePaths = {"system", "linkedTask"})
    Optional<PersonalSystemOccurrence> findByLinkedTaskIdAndOwnerId(Long linkedTaskId, Long ownerId);
}
