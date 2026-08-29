package pj.dooit.workspace.controller;

import pj.dooit.auth.service.JwtTokenService;
import pj.dooit.dday.domain.DdayGoal;
import pj.dooit.dday.repository.DdayGoalRepository;
import pj.dooit.mail.MailService;
import pj.dooit.task.domain.RecurrenceFrequency;
import pj.dooit.task.domain.RecurrenceSeries;
import pj.dooit.task.domain.Task;
import pj.dooit.task.domain.TaskType;
import pj.dooit.task.repository.RecurrenceSeriesRepository;
import pj.dooit.task.repository.TaskRepository;
import pj.dooit.user.domain.User;
import pj.dooit.user.repository.UserRepository;
import pj.dooit.workspace.domain.SharedWorkspace;
import pj.dooit.workspace.domain.WorkspaceMember;
import pj.dooit.workspace.domain.WorkspaceMemberStatus;
import pj.dooit.workspace.domain.WorkspaceRole;
import pj.dooit.workspace.repository.SharedWorkspaceRepository;
import pj.dooit.workspace.repository.WorkspaceMemberRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.batch.job.enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WorkspaceScopeIsolationIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserRepository userRepository;

    @Autowired
    JwtTokenService jwtTokenService;

    @Autowired
    TaskRepository taskRepository;

    @Autowired
    RecurrenceSeriesRepository recurrenceSeriesRepository;

    @Autowired
    DdayGoalRepository ddayGoalRepository;

    @Autowired
    SharedWorkspaceRepository sharedWorkspaceRepository;

    @Autowired
    WorkspaceMemberRepository workspaceMemberRepository;

    @MockitoBean
    MailService mailService;

    @Test
    @DisplayName("개인 Task API는 같은 owner의 workspace Task를 반환하지 않는다")
    void personalTaskApi_excludesWorkspaceTasks() throws Exception {
        User owner = userRepository.save(new User("workspace-scope-task@example.com", "encoded-password", "Scope 사용자"));
        String accessToken = jwtTokenService.createAccessToken(owner).tokenValue();
        SharedWorkspace workspace = sharedWorkspaceRepository.save(new SharedWorkspace("공유 공간", null, owner));

        taskRepository.save(Task.builder()
                .title("개인 일정")
                .type(TaskType.SCHEDULE)
                .startAt(LocalDateTime.of(2026, 8, 13, 9, 0))
                .endAt(LocalDateTime.of(2026, 8, 13, 10, 0))
                .owner(owner)
                .build());
        Task workspaceTask = Task.builder()
                .title("공유 일정")
                .type(TaskType.SCHEDULE)
                .startAt(LocalDateTime.of(2026, 8, 13, 11, 0))
                .endAt(LocalDateTime.of(2026, 8, 13, 12, 0))
                .owner(owner)
                .build();
        workspaceTask.assignWorkspace(workspace);
        workspaceTask = taskRepository.save(workspaceTask);

        mockMvc.perform(get("/api/v1/tasks")
                        .header("Authorization", "Bearer " + accessToken)
                        .param("type", "DAY")
                        .param("date", "2026-08-13"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].title").value("개인 일정"));

        mockMvc.perform(get("/api/v1/tasks/{id}", workspaceTask.getId())
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value(20001));
    }

    @Test
    @DisplayName("개인 D-Day API는 같은 owner의 workspace D-Day를 반환하지 않는다")
    void personalDdayApi_excludesWorkspaceDdays() throws Exception {
        User owner = userRepository.save(new User("workspace-scope-dday@example.com", "encoded-password", "Scope 사용자"));
        String accessToken = jwtTokenService.createAccessToken(owner).tokenValue();
        SharedWorkspace workspace = sharedWorkspaceRepository.save(new SharedWorkspace("공유 공간", null, owner));

        ddayGoalRepository.save(new DdayGoal("개인 목표", LocalDate.of(2026, 8, 31), owner));
        DdayGoal workspaceGoal = new DdayGoal("공유 목표", LocalDate.of(2026, 9, 30), owner);
        workspaceGoal.assignWorkspace(workspace);
        workspaceGoal = ddayGoalRepository.save(workspaceGoal);

        mockMvc.perform(get("/api/v1/dday-goals")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].title").value("개인 목표"));

        mockMvc.perform(get("/api/v1/dday-goals/{id}", workspaceGoal.getId())
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value(30001));
    }

    @Test
    @DisplayName("workspace Task는 personal D-Day에 연결할 수 없다")
    void workspaceTask_rejectsPersonalDdayConnection() throws Exception {
        User owner = userRepository.save(new User("workspace-scope-connect@example.com", "encoded-password", "Scope 사용자"));
        String accessToken = jwtTokenService.createAccessToken(owner).tokenValue();
        SharedWorkspace workspace = createOwnedWorkspace(owner, "공유 공간");
        DdayGoal personalGoal = ddayGoalRepository.save(new DdayGoal("개인 목표", LocalDate.of(2026, 9, 30), owner));
        Task workspaceTask = Task.builder()
                .title("공유 일정")
                .type(TaskType.TODO)
                .owner(owner)
                .build();
        workspaceTask.assignWorkspace(workspace);
        workspaceTask = taskRepository.save(workspaceTask);

        mockMvc.perform(patch("/api/v1/workspaces/{workspaceId}/tasks/{taskId}/dday-goal", workspace.getId(), workspaceTask.getId())
                        .header("Authorization", "Bearer " + accessToken)
                        .param("ddayGoalId", personalGoal.getId().toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value(30001));
    }

    @Test
    @DisplayName("workspace 반복 occurrence materialize는 다른 workspace 조회에 섞이지 않는다")
    void workspaceOccurrenceMaterialize_staysInsideWorkspace() throws Exception {
        User owner = userRepository.save(new User("workspace-scope-recurrence@example.com", "encoded-password", "Scope 사용자"));
        String accessToken = jwtTokenService.createAccessToken(owner).tokenValue();
        SharedWorkspace workspace = createOwnedWorkspace(owner, "공유 공간");
        SharedWorkspace otherWorkspace = createOwnedWorkspace(owner, "다른 공유 공간");
        RecurrenceSeries series = new RecurrenceSeries(
                owner,
                RecurrenceFrequency.WEEKLY,
                1,
                "FREQ=WEEKLY;INTERVAL=1;BYDAY=MO;COUNT=2",
                "Asia/Seoul",
                LocalDateTime.of(2026, 8, 17, 9, 0),
                null,
                2
        );
        series.assignWorkspace(workspace);
        series = recurrenceSeriesRepository.save(series);
        Task template = Task.builder()
                .title("공유 반복 회의")
                .type(TaskType.SCHEDULE)
                .startAt(LocalDateTime.of(2026, 8, 17, 9, 0))
                .endAt(LocalDateTime.of(2026, 8, 17, 10, 0))
                .owner(owner)
                .recurrenceSeries(series)
                .occurrenceDate(LocalDate.of(2026, 8, 17))
                .originalOccurrenceDate(LocalDate.of(2026, 8, 17))
                .build();
        template.assignWorkspace(workspace);
        taskRepository.save(template);

        mockMvc.perform(get("/api/v1/workspaces/{workspaceId}/tasks", workspace.getId())
                        .header("Authorization", "Bearer " + accessToken)
                        .param("type", "DAY")
                        .param("taskType", "SCHEDULE")
                        .param("date", "2026-08-24"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].occurrenceDate").value("2026-08-24"));

        mockMvc.perform(get("/api/v1/workspaces/{workspaceId}/tasks", otherWorkspace.getId())
                        .header("Authorization", "Bearer " + accessToken)
                        .param("type", "DAY")
                        .param("taskType", "SCHEDULE")
                        .param("date", "2026-08-24"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    private SharedWorkspace createOwnedWorkspace(User owner, String name) {
        SharedWorkspace workspace = sharedWorkspaceRepository.save(new SharedWorkspace(name, null, owner));
        workspaceMemberRepository.save(new WorkspaceMember(
                workspace,
                owner,
                WorkspaceRole.OWNER,
                WorkspaceMemberStatus.ACTIVE,
                owner
        ));
        return workspace;
    }
}
