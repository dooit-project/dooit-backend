package com.todolab.workspace.repository;

import com.todolab.workspace.domain.SharedWorkspace;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SharedWorkspaceRepository extends JpaRepository<SharedWorkspace, Long> {
}
