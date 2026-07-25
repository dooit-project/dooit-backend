package com.todolab.docs;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DateTimeContractTest {

    @Test
    @DisplayName("main 코드는 날짜/시간 현재값을 서비스 기준 timezone으로 계산한다")
    void mainCodeUsesServiceZoneForCurrentDateTime() throws Exception {
        try (var paths = Files.walk(Path.of("src/main/java"))) {
            String mainSource = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .map(DateTimeContractTest::readString)
                    .reduce("", (left, right) -> left + "\n" + right);

            assertThat(mainSource).doesNotContain("LocalDate.now()");
            assertThat(mainSource).doesNotContain("LocalDateTime.now()");
            assertThat(mainSource).doesNotContain("ZoneId.systemDefault()");
            assertThat(mainSource).contains("Constant.ZONE");
        }
    }

    private static String readString(Path path) {
        try {
            return Files.readString(path);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
