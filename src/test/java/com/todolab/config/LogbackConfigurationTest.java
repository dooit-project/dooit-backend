package com.todolab.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class LogbackConfigurationTest {

    private static final Path LOGBACK = Path.of("src/main/resources/logback-spring.xml");

    @Test
    @DisplayName("Logback 설정은 일 단위 압축 archive와 request id 패턴을 포함한다")
    void logbackConfigDefinesRollingArchiveAndRequestIdPattern() throws Exception {
        Document document = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(LOGBACK.toFile());
        String content = java.nio.file.Files.readString(LOGBACK);

        assertThat(document.getDocumentElement().getNodeName()).isEqualTo("configuration");
        assertThat(content).contains("%X{requestId:--}");
        assertThat(content).contains("%d{yyyy-MM-dd}.%i.log.gz");
        assertThat(content).contains("SizeAndTimeBasedRollingPolicy");
        assertThat(content).contains("ASYNC_APP_FILE");
        assertThat(content).contains("ASYNC_ERROR_FILE");
        assertThat(content).contains("ThresholdFilter");
    }
}
