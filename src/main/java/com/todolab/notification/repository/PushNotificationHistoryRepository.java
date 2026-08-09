package com.todolab.notification.repository;

import com.todolab.notification.domain.PushNotificationHistory;
import com.todolab.notification.domain.PushNotificationStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PushNotificationHistoryRepository extends JpaRepository<PushNotificationHistory, Long> {

    List<PushNotificationHistory> findByOwnerIdOrderByAttemptedAtDescIdDesc(Long ownerId, Pageable pageable);

    List<PushNotificationHistory> findByOwnerId(Long ownerId);

    boolean existsByOwnerIdAndIdempotencyKeyAndStatus(Long ownerId, String idempotencyKey, PushNotificationStatus status);
}
