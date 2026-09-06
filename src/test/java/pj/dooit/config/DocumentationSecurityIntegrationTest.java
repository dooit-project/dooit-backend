package pj.dooit.config;

import pj.dooit.mail.MailService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.batch.job.enabled=false",
        "app.docs.public-enabled=false",
        "app.monitoring.enabled=true",
        "app.monitoring.username=dooit-prometheus",
        "app.monitoring.password=test-monitoring-password"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DocumentationSecurityIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    MailService mailService;

    @Test
    @DisplayName("문서 공개 설정을 끄면 OpenAPI 문서 endpoint가 공개되지 않는다")
    void documentationEndpointsAreNotPublicWhenDisabled() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/scalar.html"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("문서 공개 설정을 꺼도 actuator health endpoint는 공개한다")
    void actuatorHealthEndpointsArePublicWhenDocumentationIsDisabled() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Prometheus actuator endpoint는 모니터링 인증을 요구한다")
    void prometheusEndpointRequiresMonitoringCredentials() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/actuator/prometheus")
                        .with(httpBasic("dooit-prometheus", "wrong-password")))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/actuator/prometheus")
                        .with(httpBasic("dooit-prometheus", "test-monitoring-password")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("서버 렌더링 화면 경로는 더 이상 제공하지 않는다")
    void serverRenderedPagesAreNotServed() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/login"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/tasks/today"))
                .andExpect(status().isForbidden());
    }
}
