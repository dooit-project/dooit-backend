package pj.dooit.auth.service;

import pj.dooit.dday.domain.DdayGoal;
import pj.dooit.auth.repository.RefreshTokenSessionRepository;
import pj.dooit.dday.repository.DdayGoalRepository;
import pj.dooit.notification.domain.PushDeviceToken;
import pj.dooit.notification.domain.PushNotificationHistory;
import pj.dooit.notification.repository.PushDeviceTokenRepository;
import pj.dooit.notification.repository.PushNotificationHistoryRepository;
import pj.dooit.task.domain.RecurrenceSeries;
import pj.dooit.task.domain.Task;
import pj.dooit.task.domain.TaskTemplate;
import pj.dooit.task.repository.RecurrenceSeriesRepository;
import pj.dooit.task.repository.TaskTemplateRepository;
import pj.dooit.task.repository.TaskRepository;
import pj.dooit.user.domain.AccountType;
import pj.dooit.user.domain.User;
import pj.dooit.user.repository.UserRepository;
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
    private final TaskTemplateRepository taskTemplateRepository;
    private final PushDeviceTokenRepository pushDeviceTokenRepository;
    private final PushNotificationHistoryRepository pushNotificationHistoryRepository;
    private final RefreshTokenSessionRepository refreshTokenSessionRepository;

    @Transactional
    public CleanupResult deleteExpiredGuests(LocalDateTime now) {
        List<User> expiredGuests =
                userRepository.findByAccountTypeAndMergedIntoUserIdIsNullAndGuestExpiresAtBefore(AccountType.GUEST, now);
        int deletedTasks = 0;
        int deletedDdayGoals = 0;
        int deletedRecurrenceSeries = 0;
        int deletedTaskTemplates = 0;
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

            List<TaskTemplate> taskTemplates = taskTemplateRepository.findByOwnerId(guest.getId());
            taskTemplateRepository.deleteAll(taskTemplates);
            deletedTaskTemplates += taskTemplates.size();

            List<DdayGoal> ddayGoals = ddayGoalRepository.findAllByOwnerIdOrderByTargetDateAscIdAsc(guest.getId());
            ddayGoalRepository.deleteAll(ddayGoals);
            deletedDdayGoals += ddayGoals.size();

            refreshTokenSessionRepository.findByUserId(guest.getId())
                    .forEach(refreshTokenSessionRepository::delete);
        }

        userRepository.deleteAll(expiredGuests);
        CleanupResult result = new CleanupResult(
                expiredGuests.size(),
                deletedTasks,
                deletedDdayGoals,
                deletedRecurrenceSeries,
                deletedTaskTemplates,
                deletedPushTokens,
                deletedPushHistories
        );
        if (result.deletedGuests() > 0) {
            log.info(
                    "Expired guest accounts deleted: guests={}, tasks={}, ddayGoals={}, recurrenceSeries={}, taskTemplates={}, pushTokens={}, pushHistories={}",
                    result.deletedGuests(),
                    result.deletedTasks(),
                    result.deletedDdayGoals(),
                    result.deletedRecurrenceSeries(),
                    result.deletedTaskTemplates(),
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
            int deletedTaskTemplates,
            int deletedPushTokens,
            int deletedPushHistories
    ) {
    }
}
