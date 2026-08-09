package com.todolab.auth.service;

import com.todolab.dday.domain.DdayGoal;
import com.todolab.dday.repository.DdayGoalRepository;
import com.todolab.notification.domain.PushDeviceToken;
import com.todolab.notification.domain.PushNotificationHistory;
import com.todolab.notification.repository.PushDeviceTokenRepository;
import com.todolab.notification.repository.PushNotificationHistoryRepository;
import com.todolab.task.domain.RecurrenceSeries;
import com.todolab.task.domain.Task;
import com.todolab.task.repository.RecurrenceSeriesRepository;
import com.todolab.task.repository.TaskRepository;
import com.todolab.user.domain.AccountType;
import com.todolab.user.domain.User;
import com.todolab.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class GuestAccountCleanupService {

    private final UserRepository userRepository;
    private final TaskRepository taskRepository;
    private final DdayGoalRepository ddayGoalRepository;
    private final RecurrenceSeriesRepository recurrenceSeriesRepository;
    private final PushDeviceTokenRepository pushDeviceTokenRepository;
    private final PushNotificationHistoryRepository pushNotificationHistoryRepository;

    @Transactional
    public CleanupResult deleteExpiredGuests(LocalDateTime now) {
        List<User> expiredGuests =
                userRepository.findByAccountTypeAndMergedIntoUserIdIsNullAndGuestExpiresAtBefore(AccountType.GUEST, now);
        int deletedTasks = 0;
        int deletedDdayGoals = 0;
        int deletedRecurrenceSeries = 0;
        int deletedPushTokens = 0;
        int deletedPushHistories = 0;

        for (User guest : expiredGuests) {
            List<PushNotificationHistory> pushHistories = pushNotificationHistoryRepository.findByOwnerId(guest.getId());
            pushNotificationHistoryRepository.deleteAll(pushHistories);
            deletedPushHistories += pushHistories.size();

            List<PushDeviceToken> pushTokens = pushDeviceTokenRepository.findByOwnerId(guest.getId());
            pushDeviceTokenRepository.deleteAll(pushTokens);
            deletedPushTokens += pushTokens.size();

            List<Task> tasks = taskRepository.findByOwnerId(guest.getId());
            taskRepository.deleteAll(tasks);
            deletedTasks += tasks.size();

            List<RecurrenceSeries> recurrenceSeries = recurrenceSeriesRepository.findByOwnerId(guest.getId());
            recurrenceSeriesRepository.deleteAll(recurrenceSeries);
            deletedRecurrenceSeries += recurrenceSeries.size();

            List<DdayGoal> ddayGoals = ddayGoalRepository.findAllByOwnerIdOrderByTargetDateAscIdAsc(guest.getId());
            ddayGoalRepository.deleteAll(ddayGoals);
            deletedDdayGoals += ddayGoals.size();
        }

        userRepository.deleteAll(expiredGuests);
        CleanupResult result = new CleanupResult(
                expiredGuests.size(),
                deletedTasks,
                deletedDdayGoals,
                deletedRecurrenceSeries,
                deletedPushTokens,
                deletedPushHistories
        );
        if (result.deletedGuests() > 0) {
            log.info(
                    "Expired guest accounts deleted: guests={}, tasks={}, ddayGoals={}, recurrenceSeries={}, pushTokens={}, pushHistories={}",
                    result.deletedGuests(),
                    result.deletedTasks(),
                    result.deletedDdayGoals(),
                    result.deletedRecurrenceSeries(),
                    result.deletedPushTokens(),
                    result.deletedPushHistories()
            );
        }
        return result;
    }

    public record CleanupResult(
            int deletedGuests,
            int deletedTasks,
            int deletedDdayGoals,
            int deletedRecurrenceSeries,
            int deletedPushTokens,
            int deletedPushHistories
    ) {
    }
}
