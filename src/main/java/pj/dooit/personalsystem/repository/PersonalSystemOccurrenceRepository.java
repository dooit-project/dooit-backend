package pj.dooit.personalsystem.repository;

import pj.dooit.personalsystem.domain.PersonalSystemOccurrence;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PersonalSystemOccurrenceRepository extends JpaRepository<PersonalSystemOccurrence, Long> {

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

    @EntityGraph(attributePaths = {"system", "linkedTask"})
    Optional<PersonalSystemOccurrence> findByIdAndOwnerId(Long id, Long ownerId);

    @EntityGraph(attributePaths = {"system", "linkedTask"})
    Optional<PersonalSystemOccurrence> findByLinkedTaskIdAndOwnerId(Long linkedTaskId, Long ownerId);
}
