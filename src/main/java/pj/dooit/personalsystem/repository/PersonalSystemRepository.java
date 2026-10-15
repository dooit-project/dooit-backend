package pj.dooit.personalsystem.repository;

import pj.dooit.personalsystem.domain.PersonalSystem;
import pj.dooit.personalsystem.domain.PersonalSystemStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import java.util.Optional;

public interface PersonalSystemRepository extends JpaRepository<PersonalSystem, Long> {

    List<PersonalSystem> findByOwnerIdOrderByCreatedAtDescIdDesc(Long ownerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PersonalSystem> findLockedByIdAndOwnerId(Long id, Long ownerId);

    Optional<PersonalSystem> findByIdAndOwnerId(Long id, Long ownerId);

    long countByOwnerIdAndStatus(Long ownerId, PersonalSystemStatus status);
}
