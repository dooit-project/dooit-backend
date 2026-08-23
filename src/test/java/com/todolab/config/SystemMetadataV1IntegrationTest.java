package com.todolab.config;

import com.todolab.mail.MailService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.batch.job.enabled=false",
        "app.metadata.version=1.0-test",
        "app.metadata.commit-sha=test-commit-sha",
        "app.metadata.image-tag=test-image-tag"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SystemMetadataV1IntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    MailService mailService;

    @Test
    @DisplayName("v1 system metadata는 인증 없이 backend 배포 식별자를 반환한다")
    void metadata_successWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/system/metadata"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.version").value("1.0-test"))
                .andExpect(jsonPath("$.data.commitSha").value("test-commit-sha"))
                .andExpect(jsonPath("$.data.imageTag").value("test-image-tag"));
    }
}
