package pj.dooit.goal.repository;

import pj.dooit.goal.domain.Goal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GoalRepository extends JpaRepository<Goal, Long> {

    List<Goal> findByOwnerIdOrderByCreatedAtDescIdDesc(Long ownerId);

    Optional<Goal> findByIdAndOwnerId(Long id, Long ownerId);
}
