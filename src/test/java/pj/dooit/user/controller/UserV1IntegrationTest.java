package pj.dooit.user.controller;

import pj.dooit.auth.service.JwtTokenService;
import pj.dooit.mail.MailService;
import pj.dooit.user.domain.User;
import pj.dooit.user.dto.UserTimeZoneRequest;
import pj.dooit.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.batch.job.enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserV1IntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    UserRepository userRepository;

    @Autowired
    JwtTokenService jwtTokenService;

    @MockitoBean
    MailService mailService;

    @Test
    @DisplayName("v1 사용자는 timezone 설정값을 저장한다")
    void updateMyTimeZone_success() throws Exception {
        User owner = userRepository.save(new User("timezone-owner@example.com", "encoded-password", "Timezone 사용자"));
        String accessToken = jwtTokenService.createAccessToken(owner).tokenValue();

        mockMvc.perform(patch("/api/v1/users/me/time-zone")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UserTimeZoneRequest("America/New_York"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.email").value("timezone-owner@example.com"))
                .andExpect(jsonPath("$.data.timeZone").value("America/New_York"));

        assertThat(userRepository.findById(owner.getId()).orElseThrow().getTimeZone()).isEqualTo("America/New_York");
    }

    @Test
    @DisplayName("v1 사용자는 유효하지 않은 timezone 설정을 거부한다")
    void updateMyTimeZone_fail_invalidTimeZone() throws Exception {
        User owner = userRepository.save(new User("timezone-invalid@example.com", "encoded-password", "Timezone 사용자"));
        String accessToken = jwtTokenService.createAccessToken(owner).tokenValue();

        mockMvc.perform(patch("/api/v1/users/me/time-zone")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UserTimeZoneRequest("Not/AZone"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("fail"))
                .andExpect(jsonPath("$.error.code").value(10001));
    }
}
