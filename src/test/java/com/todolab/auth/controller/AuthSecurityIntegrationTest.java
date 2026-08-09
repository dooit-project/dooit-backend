package com.todolab.auth.controller;

import com.todolab.auth.security.ApiAccessDeniedHandler;
import com.todolab.auth.dto.LoginRequest;
import com.todolab.auth.dto.RegisterRequest;
import com.todolab.auth.service.JwtTokenService;
import com.todolab.common.api.ErrorCode;
import com.todolab.dday.domain.DdayGoal;
import com.todolab.dday.repository.DdayGoalRepository;
import com.todolab.mail.MailService;
import com.todolab.task.domain.RecurrenceFrequency;
import com.todolab.task.domain.RecurrenceSeries;
import com.todolab.task.domain.Task;
import com.todolab.task.domain.TaskType;
import com.todolab.task.repository.RecurrenceSeriesRepository;
import com.todolab.task.repository.TaskRepository;
import com.todolab.user.domain.User;
import com.todolab.user.repository.UserRepository;
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

    @MockitoBean
    MailService mailService;

    @BeforeEach
    void setUp() {
        taskRepository.deleteAll();
        recurrenceSeriesRepository.deleteAll();
        ddayGoalRepository.deleteAll();
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
                .andExpect(jsonPath("$.data.user.accountType").value("REGISTERED"));

        assertThat(taskRepository.findById(task.getId())).get()
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
                .andExpect(jsonPath("$.error.code").value(ErrorCode.UNAUTHORIZED.getCode()));
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
