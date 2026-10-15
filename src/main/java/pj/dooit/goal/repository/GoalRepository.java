package pj.dooit.goal.repository;

import pj.dooit.goal.domain.Goal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import java.util.Optional;

public interface GoalRepository extends JpaRepository<Goal, Long> {

    List<Goal> findByOwnerIdOrderByCreatedAtDescIdDesc(Long ownerId);

    Optional<Goal> findByIdAndOwnerId(Long id, Long ownerId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from Goal g where g.id = :id and g.owner.id = :ownerId")
    Optional<Goal> findForUpdate(Long id, Long ownerId);

    boolean existsByIdAndOwnerId(Long id, Long ownerId);
}
