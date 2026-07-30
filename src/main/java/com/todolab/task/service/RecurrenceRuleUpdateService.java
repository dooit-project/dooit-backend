package com.todolab.task.service;

import com.todolab.task.domain.RecurrenceSeries;
import com.todolab.task.domain.Task;
import com.todolab.task.domain.TaskStatus;
import com.todolab.task.dto.TaskRecurrenceRequest;
import com.todolab.task.exception.TaskNotFoundException;
import com.todolab.task.exception.TaskValidationException;
import com.todolab.task.repository.RecurrenceSeriesRepository;
import com.todolab.task.repository.TaskRepository;
import com.todolab.user.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RecurrenceRuleUpdateService {

    private final TaskRepository taskRepository;
    private final RecurrenceSeriesRepository recurrenceSeriesRepository;

    @Transactional
    public Task updateRuleFromOccurrenceForOwner(Long taskId, TaskRecurrenceRequest request, User owner) {
        if (request == null) {
            throw new TaskValidationException("반복 규칙 요청은 필수입니다.");
        }
        Long ownerId = ownerId(owner);
        Task selectedOccurrence = taskRepository.findByIdAndOwnerId(taskId, ownerId)
                .orElseThrow(() -> new TaskNotFoundException(taskId));
        if (selectedOccurrence.getRecurrenceSeries() == null || selectedOccurrence.getOccurrenceDate() == null) {
            throw new TaskValidationException("반복 occurrence만 반복 규칙을 변경할 수 있습니다.");
        }
        if (selectedOccurrence.getStartAt() == null) {
            throw new TaskValidationException("반복 규칙 변경은 기준 occurrence의 시작 일시가 필요합니다.");
        }

        request.validate(selectedOccurrence.getStartAt());
        RecurrenceSeries series = recurrenceSeriesRepository.findByIdAndOwnerId(
                        selectedOccurrence.getRecurrenceSeries().getId(),
                        ownerId
                )
                .orElseThrow(() -> new TaskNotFoundException(taskId));
        series.update(
                request.normalizedFrequency(),
                request.normalizedInterval(),
                request.normalizedRecurrenceRule(),
                request.normalizedTimeZone(),
                selectedOccurrence.getStartAt(),
                request.recurrenceUntil(),
                request.recurrenceCount()
        );

        deleteFuturePlainOccurrences(series.getId(), ownerId, selectedOccurrence.getOccurrenceDate());
        return selectedOccurrence;
    }

    private void deleteFuturePlainOccurrences(Long recurrenceSeriesId, Long ownerId, LocalDate effectiveDate) {
        List<Task> deleteTargets = taskRepository
                .findByRecurrenceSeriesIdAndOwnerIdAndOccurrenceDateGreaterThanEqualOrderByOccurrenceDateAscIdAsc(
                        recurrenceSeriesId,
                        ownerId,
                        effectiveDate.plusDays(1)
                )
                .stream()
                .filter(task -> task.getStatus() != TaskStatus.DONE)
                .filter(task -> task.getRecurrenceException() == null)
                .toList();
        taskRepository.deleteAll(deleteTargets);
    }

    private Long ownerId(User owner) {
        if (owner == null || owner.getId() == null) {
            throw new IllegalArgumentException("owner는 영속화된 사용자여야 합니다.");
        }
        return owner.getId();
    }
}
