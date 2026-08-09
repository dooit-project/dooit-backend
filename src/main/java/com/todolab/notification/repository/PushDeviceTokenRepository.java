package com.todolab.notification.repository;

import com.todolab.notification.domain.PushDeviceToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PushDeviceTokenRepository extends JpaRepository<PushDeviceToken, Long> {

    List<PushDeviceToken> findByOwnerIdAndActiveTrueOrderByLastRegisteredAtDescIdDesc(Long ownerId);

    List<PushDeviceToken> findByOwnerId(Long ownerId);

    Optional<PushDeviceToken> findByOwnerIdAndDeviceToken(Long ownerId, String deviceToken);

    Optional<PushDeviceToken> findByIdAndOwnerId(Long id, Long ownerId);
}
