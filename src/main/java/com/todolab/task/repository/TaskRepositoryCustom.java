package com.todolab.task.repository;

import com.todolab.task.domain.Task;
import com.todolab.task.domain.TaskStatus;
import com.todolab.task.domain.TaskType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface TaskRepositoryCustom {
    List<Task> findByDateRange(LocalDateTime start, LocalDateTime end);

    List<Task> findByDateRange(Long ownerId, LocalDateTime start, LocalDateTime end);

    List<Task> findByDateRangeAndType(LocalDateTime start, LocalDateTime end, TaskType taskType);

    List<Task> findByDateRangeAndType(Long ownerId, LocalDateTime start, LocalDateTime end, TaskType taskType);

    List<Task> findUnscheduledTask();

    List<Task> findUnscheduledTask(Long ownerId);

    List<Task> findByStatus(TaskStatus status);

    List<Task> findByStatus(Long ownerId, TaskStatus status);

    List<Task> findPlannedTasks(LocalDate fromInclusive, LocalDate toExclusive);

    List<Task> findPlannedTasks(Long ownerId, LocalDate fromInclusive, LocalDate toExclusive);

    List<Task> findTodayTasks(LocalDate targetDate);

    List<Task> findTodayTasks(Long ownerId, LocalDate targetDate);

    List<Task> findTodayTasks(Long ownerId, LocalDate targetDate, LocalDateTime scheduleStart, LocalDateTime scheduleEnd);

    List<Task> findNotificationCandidateTasks(Long ownerId, LocalDateTime start, LocalDateTime end);

    List<Task> findReorderableTodayTasks(Long ownerId, LocalDate targetDate);

    Integer findMaxTodayOrder(LocalDate targetDate);

    Integer findMaxTodayOrder(Long ownerId, LocalDate targetDate);

    List<Task> findDoneTasks(LocalDate completedDate);

    List<Task> findDoneTasks(Long ownerId, LocalDate completedDate);

    List<Task> findDoneTasksBetween(LocalDate startDate, LocalDate endDate);

    List<Task> findDoneTasksBetween(Long ownerId, LocalDate startDate, LocalDate endDate);

    List<Task> findByDdayGoalId(Long ddayGoalId);

    List<Task> findByDdayGoalId(Long ownerId, Long ddayGoalId);
}
