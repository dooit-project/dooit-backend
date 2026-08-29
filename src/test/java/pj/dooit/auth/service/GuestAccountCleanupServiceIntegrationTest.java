package pj.dooit.auth.service;

import pj.dooit.calendar.repository.CalendarFeedTokenRepository;
import pj.dooit.dday.domain.DdayGoal;
import pj.dooit.dday.repository.DdayGoalRepository;
import pj.dooit.auth.repository.RefreshTokenSessionRepository;
import pj.dooit.mail.MailService;
import pj.dooit.notification.config.PushNotificationProvider;
import pj.dooit.notification.domain.PushDeviceToken;
import pj.dooit.notification.domain.PushNotificationHistory;
import pj.dooit.notification.domain.PushNotificationSource;
import pj.dooit.notification.domain.PushNotificationStatus;
import pj.dooit.notification.domain.PushPlatform;
import pj.dooit.notification.repository.PushDeviceTokenRepository;
import pj.dooit.notification.repository.PushNotificationHistoryRepository;
import pj.dooit.task.domain.RecurrenceFrequency;
import pj.dooit.task.domain.RecurrenceSeries;
import pj.dooit.task.domain.Task;
import pj.dooit.task.domain.TaskTemplate;
import pj.dooit.task.domain.TaskType;
import pj.dooit.task.repository.RecurrenceSeriesRepository;
import pj.dooit.task.repository.TaskTemplateRepository;
import pj.dooit.task.repository.TaskRepository;
import pj.dooit.user.domain.User;
import pj.dooit.user.repository.UserRepository;
import pj.dooit.workspace.repository.SharedWorkspaceRepository;
import pj.dooit.workspace.repository.WorkspaceMemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "spring.batch.job.enabled=false")
@ActiveProfiles("test")
class GuestAccountCleanupServiceIntegrationTest {

    @Autowired
    GuestAccountCleanupService guestAccountCleanupService;

    @Autowired
    UserRepository userRepository;

    @Autowired
    TaskRepository taskRepository;

    @Autowired
    DdayGoalRepository ddayGoalRepository;

    @Autowired
    RecurrenceSeriesRepository recurrenceSeriesRepository;

    @Autowired
    TaskTemplateRepository taskTemplateRepository;

    @Autowired
    WorkspaceMemberRepository workspaceMemberRepository;

    @Autowired
    SharedWorkspaceRepository sharedWorkspaceRepository;

    @Autowired
    PushDeviceTokenRepository pushDeviceTokenRepository;

    @Autowired
    PushNotificationHistoryRepository pushNotificationHistoryRepository;

    @Autowired
    RefreshTokenSessionRepository refreshTokenSessionRepository;

    @Autowired
    CalendarFeedTokenRepository calendarFeedTokenRepository;

    @MockitoBean
    MailService mailService;

    @BeforeEach
    void setUp() {
        pushNotificationHistoryRepository.deleteAll();
        pushDeviceTokenRepository.deleteAll();
        calendarFeedTokenRepository.deleteAll();
        workspaceMemberRepository.deleteAll();
        taskTemplateRepository.deleteAll();
        taskRepository.deleteAll();
        recurrenceSeriesRepository.deleteAll();
        ddayGoalRepository.deleteAll();
        sharedWorkspaceRepository.deleteAll();
        refreshTokenSessionRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("만료된 게스트와 관련 owner 데이터를 삭제하고 활성 게스트는 유지한다")
    void deleteExpiredGuests_deletesExpiredGuestOwnedDataOnly() {
        User expiredGuest = userRepository.save(User.guest(LocalDateTime.of(2026, 8, 1, 0, 0)));
        User activeGuest = userRepository.save(User.guest(LocalDateTime.of(2026, 9, 1, 0, 0)));
        DdayGoal ddayGoal = ddayGoalRepository.save(new DdayGoal(
                "만료 게스트 목표",
                LocalDate.of(2026, 12, 31),
                expiredGuest
        ));
        RecurrenceSeries series = recurrenceSeriesRepository.save(new RecurrenceSeries(
                expiredGuest,
                RecurrenceFrequency.WEEKLY,
                1,
                "FREQ=WEEKLY;INTERVAL=1;COUNT=2",
                "Asia/Seoul",
                LocalDateTime.of(2026, 8, 1, 9, 0),
                null,
                2
        ));
        TaskTemplate template = taskTemplateRepository.save(new TaskTemplate(
                expiredGuest,
                "만료 게스트 템플릿",
                null,
                TaskType.TODO,
                null,
                false,
                null,
                null,
                null,
                null,
                null
        ));
        Task task = taskRepository.save(Task.builder()
                .title("만료 게스트 할 일")
                .type(TaskType.TODO)
                .targetDate(LocalDate.of(2026, 8, 1))
                .ddayGoal(ddayGoal)
                .recurrenceSeries(series)
                .occurrenceDate(LocalDate.of(2026, 8, 1))
                .owner(expiredGuest)
                .build());
        PushDeviceToken pushToken = pushDeviceTokenRepository.save(new PushDeviceToken(
                expiredGuest,
                PushPlatform.EXPO,
                "ExpoPushToken[expired]",
                "1.0.0",
                "iPhone"
        ));
        PushNotificationHistory history = pushNotificationHistoryRepository.save(new PushNotificationHistory(
                expiredGuest,
                pushToken,
                PushNotificationSource.SERVER,
                PushNotificationProvider.EXPO,
                PushNotificationStatus.SUCCESS,
                task.getId(),
                series.getId(),
                LocalDate.of(2026, 8, 1),
                "task:" + task.getId(),
                "idem:" + task.getId(),
                "message-id",
                null,
                null,
                LocalDateTime.of(2026, 8, 1, 8, 55)
        ));
        taskRepository.save(Task.builder()
                .title("활성 게스트 할 일")
                .type(TaskType.TODO)
                .targetDate(LocalDate.of(2026, 8, 9))
                .owner(activeGuest)
                .build());

        GuestAccountCleanupService.CleanupResult result =
                guestAccountCleanupService.deleteExpiredGuests(LocalDateTime.of(2026, 8, 9, 0, 0));

        assertThat(result.deletedGuests()).isEqualTo(1);
        assertThat(result.deletedTasks()).isEqualTo(1);
        assertThat(result.deletedDdayGoals()).isEqualTo(1);
        assertThat(result.deletedRecurrenceSeries()).isEqualTo(1);
        assertThat(result.deletedTaskTemplates()).isEqualTo(1);
        assertThat(result.deletedPushTokens()).isEqualTo(1);
        assertThat(result.deletedPushHistories()).isEqualTo(1);
        assertThat(userRepository.findById(expiredGuest.getId())).isEmpty();
        assertThat(taskRepository.findById(task.getId())).isEmpty();
        assertThat(ddayGoalRepository.findById(ddayGoal.getId())).isEmpty();
        assertThat(recurrenceSeriesRepository.findById(series.getId())).isEmpty();
        assertThat(taskTemplateRepository.findById(template.getId())).isEmpty();
        assertThat(pushDeviceTokenRepository.findById(pushToken.getId())).isEmpty();
        assertThat(pushNotificationHistoryRepository.findById(history.getId())).isEmpty();
        assertThat(userRepository.findById(activeGuest.getId())).isPresent();
        assertThat(taskRepository.findByOwnerId(activeGuest.getId())).hasSize(1);
    }
}
