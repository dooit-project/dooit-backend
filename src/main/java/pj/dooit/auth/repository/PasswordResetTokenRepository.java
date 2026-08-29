package pj.dooit.auth.repository;

import pj.dooit.auth.domain.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    long countByEmailAndCreatedAtGreaterThanEqual(String email, LocalDateTime createdAt);
}
