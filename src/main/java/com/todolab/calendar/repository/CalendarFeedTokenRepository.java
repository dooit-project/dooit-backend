package com.todolab.calendar.repository;

import com.todolab.calendar.domain.CalendarFeedToken;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CalendarFeedTokenRepository extends JpaRepository<CalendarFeedToken, Long> {

    @EntityGraph(attributePaths = "owner")
    Optional<CalendarFeedToken> findByTokenHashAndActiveTrue(String tokenHash);

    List<CalendarFeedToken> findByOwnerIdAndActiveTrue(Long ownerId);
}
