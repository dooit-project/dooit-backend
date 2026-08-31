package pj.dooit.dailyplan.repository;

import pj.dooit.dailyplan.domain.DailyPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

public interface DailyPlanRepository extends JpaRepository<DailyPlan, Long> {

    Optional<DailyPlan> findByOwnerIdAndDate(Long ownerId, LocalDate date);

    @Modifying
    @Query(value = "delete from DAILY_PLAN_FOCUS_TASK where TASK_ID = :taskId", nativeQuery = true)
    void deleteFocusTaskReferences(@Param("taskId") Long taskId);
}
