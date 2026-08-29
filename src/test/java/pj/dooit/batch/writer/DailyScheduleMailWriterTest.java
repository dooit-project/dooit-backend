package pj.dooit.batch.writer;

import pj.dooit.batch.domain.ScheduleMailSectionContent;
import pj.dooit.batch.domain.ScheduleSectionType;
import pj.dooit.mail.MailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.*;

class DailyScheduleMailWriterTest {

    private MailService mailService;
    private DailyScheduleMailWriter writer;

    @BeforeEach
    void setUp() {
        mailService = mock(MailService.class);
        writer = new DailyScheduleMailWriter(mailService);

        ReflectionTestUtils.setField(writer, "toEmail", "test@dooit.com");
        ReflectionTestUtils.setField(writer, "baseDateParam", "2026-03-12");
    }

    @Test
    @DisplayName("chunk 가 비어 있으면 메일을 보내지 않는다")
    void write_emptyChunk_doesNotSendMail() {
        // given
        Chunk<ScheduleMailSectionContent> chunk = new Chunk<>(List.of());

        // when
        writer.write(chunk);

        // then
        then(mailService).should(never())
                .sendText(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("정상 데이터가 있으면 정렬된 본문으로 메일을 보낸다")
    void write_validItems_sendMail() {
        // given
        ScheduleMailSectionContent seed = new ScheduleMailSectionContent(
                ScheduleSectionType.SEED,
                "- 미정 일정\n"
        );

        ScheduleMailSectionContent today = new ScheduleMailSectionContent(
                ScheduleSectionType.TODAY,
                "- 회의 [2026-03-12 10:00]\n"
        );

        ScheduleMailSectionContent week = new ScheduleMailSectionContent(
                ScheduleSectionType.WEEK,
                "- 주간 일정 [2026-03-12 08:00 ~ 2026-03-14 20:00]"
        );

        Chunk<ScheduleMailSectionContent> chunk = new Chunk<>(
                List.of(seed, today, week)
        );

        String expectedBody =
                "안녕하세요. Dooit 일정 요약입니다.\n\n" +
                        "기준일: 2026-03-12\n\n" +
                        "[" + ScheduleSectionType.SEED.getTitle() + "]\n" +
                        "- 미정 일정\n\n" +
                        "[" + ScheduleSectionType.TODAY.getTitle() + "]\n" +
                        "- 회의 [2026-03-12 10:00]\n\n" +
                        "[" + ScheduleSectionType.WEEK.getTitle() + "]\n" +
                        "- 주간 일정 [2026-03-12 08:00 ~ 2026-03-14 20:00]\n";

        // when
        writer.write(chunk);

        // then
        then(mailService).should()
                .sendText(
                        eq("test@dooit.com"),
                        eq("[Dooit] 2026-03-12 일정 요약"),
                        eq(expectedBody)
                );
    }

    @Test
    @DisplayName("baseDate 파라미터가 없으면 예외가 발생한다")
    void write_withoutBaseDate_throwsException() {
        // given
        ReflectionTestUtils.setField(writer, "baseDateParam", null);

        Chunk<ScheduleMailSectionContent> chunk = new Chunk<>(
                List.of(new ScheduleMailSectionContent(
                        ScheduleSectionType.TODAY,
                        "- 회의 [2026-03-12 10:00]\n"
                ))
        );

        // when & then
        assertThatThrownBy(() -> writer.write(chunk))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("JobParameter 'baseDate' is missing in writer.");
    }

    @Test
    @DisplayName("수신자 메일 주소가 없으면 예외가 발생한다")
    void write_withoutRecipient_throwsException() {
        // given
        ReflectionTestUtils.setField(writer, "toEmail", " ");

        Chunk<ScheduleMailSectionContent> chunk = new Chunk<>(
                List.of(new ScheduleMailSectionContent(
                        ScheduleSectionType.TODAY,
                        "- 회의 [2026-03-12 [10:00]\n"
                ))
        );

        // when & then
        assertThatThrownBy(() -> writer.write(chunk))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Property 'app.mail.daily-summary.to' is missing.");
    }
}