package pj.dooit.auth.controller;

import pj.dooit.auth.security.ApiAccessDeniedHandler;
import pj.dooit.auth.dto.LoginRequest;
import pj.dooit.auth.dto.RegisterRequest;
import pj.dooit.auth.dto.RefreshRequest;
import pj.dooit.auth.dto.LogoutRequest;
import pj.dooit.auth.repository.RefreshTokenSessionRepository;
import pj.dooit.auth.service.JwtTokenService;
import pj.dooit.calendar.repository.CalendarFeedTokenRepository;
import pj.dooit.common.api.ErrorCode;
import pj.dooit.dday.domain.DdayGoal;
import pj.dooit.dday.repository.DdayGoalRepository;
import pj.dooit.mail.MailService;
import pj.dooit.task.domain.RecurrenceFrequency;
import pj.dooit.task.domain.RecurrenceSeries;
import pj.dooit.task.domain.Task;
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
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.batch.job.enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthSecurityIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JwtTokenService jwtTokenService;

    @Autowired
    ApiAccessDeniedHandler apiAccessDeniedHandler;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    UserRepository userRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

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
    RefreshTokenSessionRepository refreshTokenSessionRepository;

    @Autowired
    CalendarFeedTokenRepository calendarFeedTokenRepository;

    @MockitoBean
    MailService mailService;

    @BeforeEach
    void setUp() {
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
    @DisplayName("내 인증 정보 조회 성공 - 유효한 Bearer 토큰이면 사용자 클레임을 반환한다")
    void me_success() throws Exception {
        User user = userRepository.save(new User("test@example.com", "encoded-password", "테스터"));
        String accessToken = jwtTokenService.createAccessToken(user).tokenValue();

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.id").value(user.getId()))
                .andExpect(jsonPath("$.data.accountType").value("REGISTERED"))
                .andExpect(jsonPath("$.data.email").value("test@example.com"))
                .andExpect(jsonPath("$.data.displayName").value("테스터"))
                .andExpect(jsonPath("$.data.role").value("USER"));
    }

    @Test
    @DisplayName("내 인증 정보 조회 성공 - 게스트 Bearer 토큰이면 GUEST 계정 정보를 반환한다")
    void me_guestSuccess() throws Exception {
        User user = userRepository.save(User.guest(java.time.LocalDateTime.of(2026, 9, 9, 0, 0)));
        String accessToken = jwtTokenService.createGuestAccessToken(user).tokenValue();

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.id").value(user.getId()))
                .andExpect(jsonPath("$.data.accountType").value("GUEST"))
                .andExpect(jsonPath("$.data.email").doesNotExist())
                .andExpect(jsonPath("$.data.displayName").doesNotExist())
                .andExpect(jsonPath("$.data.role").value("USER"));
    }

    @Test
    @DisplayName("로그인은 refresh token을 반환하고 refresh는 token을 회전한다")
    void loginAndRefresh_rotatesRefreshToken() throws Exception {
        userRepository.save(new User(
                "refresh@example.com",
                passwordEncoder.encode("password123"),
                "리프레시 사용자"
        ));

        String loginBody = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("refresh@example.com", "password123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isString())
                .andExpect(jsonPath("$.data.refreshToken").isString())
                .andExpect(jsonPath("$.data.refreshExpiresAt").isString())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String refreshToken = objectMapper.readTree(loginBody).get("data").get("refreshToken").asText();

        String refreshBody = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshRequest(refreshToken))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isString())
                .andExpect(jsonPath("$.data.refreshToken").isString())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String rotatedToken = objectMapper.readTree(refreshBody).get("data").get("refreshToken").asText();
        assertThat(rotatedToken).isNotEqualTo(refreshToken);

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshRequest(refreshToken))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value(11009));
    }

    @Test
    @DisplayName("로그아웃은 refresh token을 폐기한다")
    void logout_revokesRefreshToken() throws Exception {
        User user = userRepository.save(new User(
                "logout@example.com",
                passwordEncoder.encode("password123"),
                "로그아웃 사용자"
        ));

        String loginBody = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("logout@example.com", "password123"))))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String accessToken = objectMapper.readTree(loginBody).get("data").get("accessToken").asText();
        String refreshToken = objectMapper.readTree(loginBody).get("data").get("refreshToken").asText();

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LogoutRequest(refreshToken))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshRequest(refreshToken))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value(11009));

        assertThat(refreshTokenSessionRepository.findByUserIdAndRevokedAtIsNull(user.getId())).isEmpty();
    }

    @Test
    @DisplayName("게스트 token으로 회원가입하면 같은 user id가 정식 계정으로 승격되고 기존 guest token은 무효화된다")
    void register_promotesGuestAndInvalidatesGuestToken() throws Exception {
        String guestToken = mockMvc.perform(post("/api/v1/auth/guest"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.user.accountType").value("GUEST"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        Long guestUserId = objectMapper.readTree(guestToken).get("data").get("user").get("id").longValue();
        String guestAccessToken = objectMapper.readTree(guestToken).get("data").get("accessToken").asText();

        RegisterRequest request = new RegisterRequest("guest-promote@example.com", "password123", "승격 사용자");
        String registeredToken = mockMvc.perform(post("/api/v1/auth/register")
                        .header("Authorization", "Bearer " + guestAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.accessToken").isString())
                .andExpect(jsonPath("$.data.user.id").value(guestUserId))
                .andExpect(jsonPath("$.data.user.accountType").value("REGISTERED"))
                .andExpect(jsonPath("$.data.user.email").value("guest-promote@example.com"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String registeredAccessToken = objectMapper.readTree(registeredToken).get("data").get("accessToken").asText();

        assertThat(userRepository.findById(guestUserId)).get()
                .satisfies(user -> {
                    assertThat(user.getAccountType().name()).isEqualTo("REGISTERED");
                    assertThat(user.getEmail()).isEqualTo("guest-promote@example.com");
                    assertThat(user.getGuestExpiresAt()).isNull();
                });

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + guestAccessToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value(ErrorCode.UNAUTHORIZED.getCode()));

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + registeredAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(guestUserId))
                .andExpect(jsonPath("$.data.accountType").value("REGISTERED"));
    }

    @Test
    @DisplayName("게스트 token으로 기존 계정 로그인하면 게스트 owner 데이터가 정식 계정으로 병합된다")
    void login_mergesGuestOwnedDataIntoRegisteredUser() throws Exception {
        User registered = userRepository.save(new User(
                "merge-target@example.com",
                passwordEncoder.encode("password123"),
                "정식 사용자"
        ));
        User guest = userRepository.save(User.guest(java.time.LocalDateTime.of(2026, 9, 9, 0, 0)));
        DdayGoal ddayGoal = ddayGoalRepository.save(new DdayGoal(
                "게스트 목표",
                java.time.LocalDate.of(2026, 12, 31),
                guest
        ));
        Task task = taskRepository.save(Task.builder()
                .title("게스트 할 일")
                .type(TaskType.TODO)
                .targetDate(java.time.LocalDate.of(2026, 8, 9))
                .ddayGoal(ddayGoal)
                .owner(guest)
                .build());
        Task schedule = taskRepository.save(Task.builder()
                .title("게스트 일정")
                .type(TaskType.SCHEDULE)
                .startAt(java.time.LocalDateTime.of(2026, 8, 9, 9, 0))
                .endAt(java.time.LocalDateTime.of(2026, 8, 9, 10, 0))
                .owner(guest)
                .build());
        RecurrenceSeries series = recurrenceSeriesRepository.save(new RecurrenceSeries(
                guest,
                RecurrenceFrequency.WEEKLY,
                1,
                "FREQ=WEEKLY;INTERVAL=1;BYDAY=SU;COUNT=2",
                "Asia/Seoul",
                java.time.LocalDateTime.of(2026, 8, 9, 9, 0),
                null,
                2
        ));
        String guestAccessToken = jwtTokenService.createGuestAccessToken(guest).tokenValue();

        LoginRequest request = new LoginRequest("merge-target@example.com", "password123");
        mockMvc.perform(post("/api/v1/auth/login")
                        .header("Authorization", "Bearer " + guestAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.id").value(registered.getId()))
                .andExpect(jsonPath("$.data.user.accountType").value("REGISTERED"))
                .andExpect(jsonPath("$.data.mergeResult.tasks").value(1))
                .andExpect(jsonPath("$.data.mergeResult.schedules").value(1))
                .andExpect(jsonPath("$.data.mergeResult.ddayGoals").value(1))
                .andExpect(jsonPath("$.data.mergeResult.recurrenceSeries").value(1));

        assertThat(taskRepository.findById(task.getId())).get()
                .extracting(saved -> saved.getOwner().getId())
                .isEqualTo(registered.getId());
        assertThat(taskRepository.findById(schedule.getId())).get()
                .extracting(saved -> saved.getOwner().getId())
                .isEqualTo(registered.getId());
        assertThat(ddayGoalRepository.findById(ddayGoal.getId())).get()
                .extracting(saved -> saved.getOwner().getId())
                .isEqualTo(registered.getId());
        assertThat(recurrenceSeriesRepository.findById(series.getId())).get()
                .extracting(saved -> saved.getOwner().getId())
                .isEqualTo(registered.getId());
        assertThat(userRepository.findById(guest.getId())).get()
                .satisfies(savedGuest -> assertThat(savedGuest.getMergedIntoUserId()).isEqualTo(registered.getId()));

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + guestAccessToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value(ErrorCode.GUEST_SESSION_EXPIRED.getCode()));
    }

    @Test
    @DisplayName("만료된 게스트 token은 전용 오류 코드로 인증 실패한다")
    void me_expiredGuestSession() throws Exception {
        User guest = userRepository.save(User.guest(java.time.LocalDateTime.of(2020, 1, 1, 0, 0)));
        String guestAccessToken = jwtTokenService.createGuestAccessToken(guest).tokenValue();

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + guestAccessToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value("fail"))
                .andExpect(jsonPath("$.error.code").value(ErrorCode.GUEST_SESSION_EXPIRED.getCode()));
    }

    @Test
    @DisplayName("게스트 병합 로그인을 재시도해도 owner 데이터는 중복 이전되지 않는다")
    void login_mergeRetryIsIdempotent() throws Exception {
        User registered = userRepository.save(new User(
                "merge-retry@example.com",
                passwordEncoder.encode("password123"),
                "정식 사용자"
        ));
        User guest = userRepository.save(User.guest(java.time.LocalDateTime.of(2026, 9, 9, 0, 0)));
        Task task = taskRepository.save(Task.builder()
                .title("재시도 게스트 할 일")
                .type(TaskType.TODO)
                .targetDate(java.time.LocalDate.of(2026, 8, 9))
                .owner(guest)
                .build());
        String guestAccessToken = jwtTokenService.createGuestAccessToken(guest).tokenValue();
        LoginRequest request = new LoginRequest("merge-retry@example.com", "password123");

        mockMvc.perform(post("/api/v1/auth/login")
                        .header("Authorization", "Bearer " + guestAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.id").value(registered.getId()));

        mockMvc.perform(post("/api/v1/auth/login")
                        .header("Authorization", "Bearer " + guestAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.id").value(registered.getId()));

        assertThat(taskRepository.findById(task.getId())).get()
                .extracting(saved -> saved.getOwner().getId())
                .isEqualTo(registered.getId());
        assertThat(taskRepository.findByOwnerId(registered.getId()))
                .extracting(Task::getId)
                .containsExactly(task.getId());
        assertThat(userRepository.findById(guest.getId())).get()
                .satisfies(savedGuest -> assertThat(savedGuest.getMergedIntoUserId()).isEqualTo(registered.getId()));
    }

    @Test
    @DisplayName("기존 계정 로그인 실패 시 게스트 owner 데이터는 변경되지 않는다")
    void login_mergeKeepsGuestDataWhenPasswordInvalid() throws Exception {
        userRepository.save(new User(
                "merge-fail@example.com",
                passwordEncoder.encode("password123"),
                "정식 사용자"
        ));
        User guest = userRepository.save(User.guest(java.time.LocalDateTime.of(2026, 9, 9, 0, 0)));
        Task task = taskRepository.save(Task.builder()
                .title("게스트 할 일")
                .type(TaskType.TODO)
                .targetDate(java.time.LocalDate.of(2026, 8, 9))
                .owner(guest)
                .build());
        String guestAccessToken = jwtTokenService.createGuestAccessToken(guest).tokenValue();

        LoginRequest request = new LoginRequest("merge-fail@example.com", "wrong-password");
        mockMvc.perform(post("/api/v1/auth/login")
                        .header("Authorization", "Bearer " + guestAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value(ErrorCode.INVALID_CREDENTIALS.getCode()));

        assertThat(taskRepository.findById(task.getId())).get()
                .extracting(saved -> saved.getOwner().getId())
                .isEqualTo(guest.getId());
        assertThat(userRepository.findById(guest.getId())).get()
                .satisfies(savedGuest -> assertThat(savedGuest.getMergedIntoUserId()).isNull());
    }

    @Test
    @DisplayName("만료된 게스트 token으로 기존 계정 로그인하면 병합하지 않고 전용 오류 코드를 반환한다")
    void login_mergeFailsForExpiredGuest() throws Exception {
        User registered = userRepository.save(new User(
                "merge-expired@example.com",
                passwordEncoder.encode("password123"),
                "정식 사용자"
        ));
        User guest = userRepository.save(User.guest(java.time.LocalDateTime.of(2020, 1, 1, 0, 0)));
        Task task = taskRepository.save(Task.builder()
                .title("만료 게스트 할 일")
                .type(TaskType.TODO)
                .targetDate(java.time.LocalDate.of(2026, 8, 9))
                .owner(guest)
                .build());
        String guestAccessToken = jwtTokenService.createGuestAccessToken(guest).tokenValue();

        mockMvc.perform(post("/api/v1/auth/login")
                        .header("Authorization", "Bearer " + guestAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("merge-expired@example.com", "password123"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value(ErrorCode.GUEST_SESSION_EXPIRED.getCode()));

        assertThat(taskRepository.findById(task.getId())).get()
                .extracting(saved -> saved.getOwner().getId())
                .isEqualTo(guest.getId());
        assertThat(userRepository.findById(guest.getId())).get()
                .satisfies(savedGuest -> assertThat(savedGuest.getMergedIntoUserId()).isNull());
        assertThat(taskRepository.findByOwnerId(registered.getId())).isEmpty();
    }

    @Test
    @DisplayName("내 인증 정보 조회 실패 - 토큰이 없으면 401을 반환한다")
    void me_unauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value("fail"))
                .andExpect(jsonPath("$.error.code").value(ErrorCode.UNAUTHORIZED.getCode()));
    }

    @Test
    @DisplayName("API 접근 권한이 부족하면 403 오류 envelope를 반환한다")
    void apiForbiddenEnvelope() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        apiAccessDeniedHandler.handle(
                new MockHttpServletRequest("GET", "/api/v1/tasks"),
                response,
                new AccessDeniedException("denied")
        );

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("\"code\":11003");
        assertThat(response.getContentAsString()).contains("접근 권한이 없습니다.");
    }
}
