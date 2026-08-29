package pj.dooit.task.repository;

import pj.dooit.task.domain.TaskTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TaskTemplateRepository extends JpaRepository<TaskTemplate, Long> {

    List<TaskTemplate> findAllByOwnerIdOrderByIdAsc(Long ownerId);

    Optional<TaskTemplate> findByIdAndOwnerId(Long id, Long ownerId);

    List<TaskTemplate> findByOwnerId(Long ownerId);
}
