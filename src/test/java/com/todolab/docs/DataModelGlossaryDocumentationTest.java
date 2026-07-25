package com.todolab.docs;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DataModelGlossaryDocumentationTest {

    private static final Path GLOSSARY = Path.of("docs/DATA_MODEL_GLOSSARY.md");

    @Test
    @DisplayName("데이터 모델 사전은 주요 Task 상태와 날짜 규칙을 설명한다")
    void glossaryDocumentsTaskStateAndDateRules() throws Exception {
        String content = Files.readString(GLOSSARY);

        assertThat(content).contains("Task 상태 전이");
        assertThat(content).contains("`status=INBOX`");
        assertThat(content).contains("`status=TODAY`");
        assertThat(content).contains("`status=DONE`");
        assertThat(content).contains("여러 날 일정 overlap은 `[startAt, endAt)` 기준");
    }

    @Test
    @DisplayName("데이터 모델 사전은 User, D-Day, 반복, owner scope를 포함한다")
    void glossaryDocumentsCoreModels() throws Exception {
        String content = Files.readString(GLOSSARY);

        assertThat(content).contains("## User");
        assertThat(content).contains("## D-Day");
        assertThat(content).contains("## 반복 모델");
        assertThat(content).contains("## Owner Scope");
        assertThat(content).contains("RECURRENCE_MODEL.md");
    }
}
