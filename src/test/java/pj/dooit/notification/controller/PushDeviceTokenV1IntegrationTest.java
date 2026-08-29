package pj.dooit.notification.controller;

import pj.dooit.auth.service.JwtTokenService;
import pj.dooit.mail.MailService;
import pj.dooit.notification.domain.PushPlatform;
import pj.dooit.notification.dto.PushDeviceTokenRequest;
import pj.dooit.notification.repository.PushDeviceTokenRepository;
import pj.dooit.user.domain.User;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.batch.job.enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PushDeviceTokenV1IntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    UserRepository userRepository;

    @Autowired
    PushDeviceTokenRepository pushDeviceTokenRepository;

    @Autowired
    JwtTokenService jwtTokenService;

    @MockitoBean
    MailService mailService;

    @Test
    @DisplayName("v1 push token은 owner 범위에서 등록, 갱신, 조회, 해제한다")
    void pushToken_registerListDeactivate_ownerScoped() throws Exception {
        String accessToken = accessToken("push-token-owner@example.com");
        String deviceToken = "ExponentPushToken[abcdefghijklmnopqrstuvwxyz]";
        PushDeviceTokenRequest request = new PushDeviceTokenRequest(
                PushPlatform.EXPO,
                deviceToken,
                "1.0.0",
                "owner iPhone"
        );

        String registerResponse = mockMvc.perform(post("/api/v1/push-tokens")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.platform").value("EXPO"))
                .andExpect(jsonPath("$.data.tokenSuffix").value("vwxyz]"))
                .andExpect(jsonPath("$.data.active").value(true))
                .andExpect(jsonPath("$.data.deviceName").value("owner iPhone"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(registerResponse).doesNotContain(deviceToken);
        Number tokenId = com.jayway.jsonpath.JsonPath.read(registerResponse, "$.data.id");

        PushDeviceTokenRequest updateRequest = new PushDeviceTokenRequest(
                PushPlatform.EXPO,
                deviceToken,
                "1.0.1",
                "owner iPhone 15"
        );
        mockMvc.perform(post("/api/v1/push-tokens")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(tokenId.longValue()))
                .andExpect(jsonPath("$.data.appVersion").value("1.0.1"))
                .andExpect(jsonPath("$.data.deviceName").value("owner iPhone 15"));

        assertThat(pushDeviceTokenRepository.findAll()).hasSize(1);

        mockMvc.perform(get("/api/v1/push-tokens")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(tokenId.longValue()))
                .andExpect(jsonPath("$.data[0].active").value(true));

        mockMvc.perform(delete("/api/v1/push-tokens/{id}", tokenId.longValue())
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());

        mockMvc.perform(get("/api/v1/push-tokens")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
        assertThat(pushDeviceTokenRepository.findAll()).first().satisfies(token -> assertThat(token.isActive()).isFalse());
    }

    private String accessToken(String email) {
        User owner = userRepository.save(new User(email, "encoded-password", "Push 사용자"));
        return jwtTokenService.createAccessToken(owner).tokenValue();
    }
}
