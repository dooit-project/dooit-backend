package pj.dooit.batch.writer;

import pj.dooit.batch.domain.ScheduleMailSectionContent;
import pj.dooit.mail.MailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

@Slf4j
@Component
@StepScope
@RequiredArgsConstructor
public class DailyScheduleMailWriter implements ItemWriter<ScheduleMailSectionContent> {

    private final MailService mailService;

    @Value("${app.mail.daily-summary.to}")
    private String toEmail;

    @Value("#{jobParameters['baseDate']}")
    private String baseDateParam;

    @Override
    public void write(Chunk<? extends ScheduleMailSectionContent> chunk) {
        List<? extends ScheduleMailSectionContent> items = chunk.getItems();

        if (items.isEmpty()) {
            log.info("[BATCH] mail writer skipped. items empty");
            return;
        }

        LocalDate baseDate = getBaseDate();
        String recipient = getRecipientEmail();
        String subject = "[Dooit] " + baseDate + " 일정 요약";
        String body = buildMailBody(baseDate, items);

        log.info("[BATCH] try send mail. sectionCount={}, bodyLength={}", items.size(), body.length());

        mailService.sendText(recipient, subject, body);

        log.info("[BATCH] mail send completed");
    }

    private LocalDate getBaseDate() {
        if (baseDateParam == null || baseDateParam.isBlank()) {
            throw new IllegalStateException("JobParameter 'baseDate' is missing in writer.");
        }
        return LocalDate.parse(baseDateParam);
    }

    private String getRecipientEmail() {
        if (toEmail == null || toEmail.isBlank()) {
            throw new IllegalStateException("Property 'app.mail.daily-summary.to' is missing.");
        }
        return toEmail;
    }

    private String buildMailBody(LocalDate baseDate, List<? extends ScheduleMailSectionContent> items) {
        StringBuilder body = new StringBuilder();
        body.append("안녕하세요. Dooit 일정 요약입니다.\n\n");
        body.append("기준일: ").append(baseDate).append("\n\n");

        items.stream()
                .sorted(Comparator.comparingInt(item -> item.type().getOrder()))
                .forEach(item -> {
                    body.append("[").append(item.type().getTitle()).append("]\n");
                    body.append(item.content()).append("\n");
                });

        return body.toString();
    }
}
