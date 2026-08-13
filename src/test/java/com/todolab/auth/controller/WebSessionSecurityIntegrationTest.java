package com.todolab.auth.controller;

import com.todolab.mail.MailService;
import com.todolab.dday.repository.DdayGoalRepository;
import com.todolab.task.repository.RecurrenceSeriesRepository;
import com.todolab.task.repository.TaskTemplateRepository;
import com.todolab.task.repository.TaskRepository;
import com.todolab.user.domain.User;
import com.todolab.user.repository.UserRepository;
import com.todolab.workspace.repository.SharedWorkspaceRepository;
import com.todolab.workspace.repository.WorkspaceMemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.batch.job.enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WebSessionSecurityIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserRepository userRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    TaskRepository taskRepository;

    @Autowired
    RecurrenceSeriesRepository recurrenceSeriesRepository;

    @Autowired
    DdayGoalRepository ddayGoalRepository;

    @Autowired
    TaskTemplateRepository taskTemplateRepository;

    @Autowired
    WorkspaceMemberRepository workspaceMemberRepository;

    @Autowired
    SharedWorkspaceRepository sharedWorkspaceRepository;

    @MockitoBean
    MailService mailService;

    @BeforeEach
    void setUp() {
        workspaceMemberRepository.deleteAll();
        sharedWorkspaceRepository.deleteAll();
        taskTemplateRepository.deleteAll();
        taskRepository.deleteAll();
        recurrenceSeriesRepository.deleteAll();
        ddayGoalRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("웹 화면은 인증되지 않은 요청을 로그인 페이지로 보낸다")
    void webPage_requiresSession() throws Exception {
        mockMvc.perform(get("/tasks/today"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @DisplayName("웹 form login 성공 시 Today 화면으로 이동한다")
    void formLogin_success() throws Exception {
        userRepository.save(new User(
                "test@example.com",
                passwordEncoder.encode("password123"),
                "테스터"
        ));

        mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("username", "test@example.com")
                        .param("password", "password123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/tasks/today"));
    }
}
