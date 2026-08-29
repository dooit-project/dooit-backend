package pj.dooit.user.repository;

import pj.dooit.user.domain.AccountType;
import pj.dooit.user.domain.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<User> findWithLockById(Long id);

    List<User> findByAccountTypeAndMergedIntoUserIdIsNullAndGuestExpiresAtBefore(
            AccountType accountType,
            LocalDateTime guestExpiresAt
    );

    boolean existsByEmail(String email);
}
