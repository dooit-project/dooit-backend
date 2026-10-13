package pj.dooit.personalsystem.repository;

import pj.dooit.personalsystem.domain.PersonalSystem;
import pj.dooit.personalsystem.domain.PersonalSystemStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PersonalSystemRepository extends JpaRepository<PersonalSystem, Long> {

    List<PersonalSystem> findByOwnerIdOrderByCreatedAtDescIdDesc(Long ownerId);

    Optional<PersonalSystem> findByIdAndOwnerId(Long id, Long ownerId);

    long countByOwnerIdAndStatus(Long ownerId, PersonalSystemStatus status);
}
