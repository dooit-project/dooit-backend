package pj.dooit.workspace.repository;

import pj.dooit.workspace.domain.SharedWorkspace;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SharedWorkspaceRepository extends JpaRepository<SharedWorkspace, Long> {
}
