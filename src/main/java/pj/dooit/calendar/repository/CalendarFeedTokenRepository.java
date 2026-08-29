package pj.dooit.calendar.repository;

import pj.dooit.calendar.domain.CalendarFeedToken;
import pj.dooit.common.domain.ResourceScope;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CalendarFeedTokenRepository extends JpaRepository<CalendarFeedToken, Long> {

    @EntityGraph(attributePaths = {"owner", "workspace"})
    Optional<CalendarFeedToken> findByTokenHashAndActiveTrue(String tokenHash);

    List<CalendarFeedToken> findByOwnerIdAndActiveTrue(Long ownerId);

    List<CalendarFeedToken> findByOwnerIdAndScopeAndActiveTrue(Long ownerId, ResourceScope scope);

    List<CalendarFeedToken> findByOwnerIdAndWorkspaceIdAndScopeAndActiveTrue(
            Long ownerId,
            Long workspaceId,
            ResourceScope scope
    );

    List<CalendarFeedToken> findByWorkspaceIdAndScope(Long workspaceId, ResourceScope scope);
}
