package pj.dooit.auth.repository;

import pj.dooit.auth.domain.RefreshTokenSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RefreshTokenSessionRepository extends JpaRepository<RefreshTokenSession, Long> {

    Optional<RefreshTokenSession> findByTokenHash(String tokenHash);

    List<RefreshTokenSession> findByFamilyId(String familyId);

    List<RefreshTokenSession> findByUserIdAndRevokedAtIsNull(Long userId);

    List<RefreshTokenSession> findByUserId(Long userId);
}
