package pj.dooit.notification.repository;

import pj.dooit.notification.domain.PushDeviceToken;
import pj.dooit.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface PushDeviceTokenRepository extends JpaRepository<PushDeviceToken, Long> {

    List<PushDeviceToken> findByOwnerIdAndActiveTrueOrderByLastRegisteredAtDescIdDesc(Long ownerId);

    List<PushDeviceToken> findByOwnerId(Long ownerId);

    @Query("select distinct token.owner from PushDeviceToken token where token.active = true")
    List<User> findDistinctActiveOwners();

    Optional<PushDeviceToken> findByOwnerIdAndDeviceToken(Long ownerId, String deviceToken);

    Optional<PushDeviceToken> findByIdAndOwnerId(Long id, Long ownerId);
}
