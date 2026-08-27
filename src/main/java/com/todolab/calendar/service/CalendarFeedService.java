package com.todolab.calendar.service;

import com.todolab.Constant;
import com.todolab.calendar.domain.CalendarFeedToken;
import com.todolab.calendar.dto.CalendarFeedTokenResponse;
import com.todolab.calendar.repository.CalendarFeedTokenRepository;
import com.todolab.common.domain.ResourceScope;
import com.todolab.task.domain.Task;
import com.todolab.task.repository.TaskRepository;
import com.todolab.task.service.RecurrenceOccurrenceMaterializer;
import com.todolab.user.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CalendarFeedService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final DateTimeFormatter UTC_DATE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");
    private static final DateTimeFormatter DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final int FEED_MONTHS_BEFORE = 1;
    private static final int FEED_MONTHS_AFTER = 6;

    private final CalendarFeedTokenRepository calendarFeedTokenRepository;
    private final TaskRepository taskRepository;
    private final RecurrenceOccurrenceMaterializer recurrenceOccurrenceMaterializer;

    @Transactional
    public CalendarFeedTokenResponse issueToken(User owner) {
        Long ownerId = ownerId(owner);
        LocalDateTime now = LocalDateTime.now(Constant.ZONE);
        calendarFeedTokenRepository.findByOwnerIdAndActiveTrue(ownerId)
                .forEach(token -> token.revoke(now));

        String rawToken = generateToken();
        CalendarFeedToken token = calendarFeedTokenRepository.save(new CalendarFeedToken(owner, hash(rawToken)));
        return CalendarFeedTokenResponse.issued(token, rawToken);
    }

    @Transactional
    public void revokeTokens(User owner) {
        LocalDateTime now = LocalDateTime.now(Constant.ZONE);
        calendarFeedTokenRepository.findByOwnerIdAndActiveTrue(ownerId(owner))
                .forEach(token -> token.revoke(now));
    }

    @Transactional
    public Optional<String> renderFeed(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        Optional<CalendarFeedToken> feedToken = calendarFeedTokenRepository.findByTokenHashAndActiveTrue(hash(rawToken));
        if (feedToken.isEmpty()) {
            return Optional.empty();
        }

        User owner = feedToken.get().getOwner();
        LocalDate today = LocalDate.now(ZoneId.of(owner.getTimeZone()));
        LocalDate from = today.minusMonths(FEED_MONTHS_BEFORE).withDayOfMonth(1);
        LocalDate toExclusive = today.plusMonths(FEED_MONTHS_AFTER).plusDays(1);
        LocalDateTime fromService = from.atStartOfDay()
                .atZone(ZoneId.of(owner.getTimeZone()))
                .withZoneSameInstant(Constant.ZONE)
                .toLocalDateTime();
        LocalDateTime toService = toExclusive.atStartOfDay()
                .atZone(ZoneId.of(owner.getTimeZone()))
                .withZoneSameInstant(Constant.ZONE)
                .toLocalDateTime();

        recurrenceOccurrenceMaterializer.materializeForOwner(owner.getId(), fromService.toLocalDate(), toService.toLocalDate());
        List<Task> tasks = taskRepository.findCalendarFeedTasks(
                owner.getId(),
                ResourceScope.PERSONAL,
                fromService,
                toService
        );
        return Optional.of(render(owner, tasks));
    }

    private String render(User owner, List<Task> tasks) {
        StringBuilder builder = new StringBuilder();
        builder.append("BEGIN:VCALENDAR\r\n");
        builder.append("VERSION:2.0\r\n");
        builder.append("PRODID:-//ToDoLab//Calendar Feed//KO\r\n");
        builder.append("CALSCALE:GREGORIAN\r\n");
        builder.append("METHOD:PUBLISH\r\n");
        builder.append("X-WR-CALNAME:").append(escape("ToDoLab")).append("\r\n");
        builder.append("X-WR-TIMEZONE:").append(escape(owner.getTimeZone())).append("\r\n");
        for (Task task : tasks) {
            appendEvent(builder, task);
        }
        builder.append("END:VCALENDAR\r\n");
        return builder.toString();
    }

    private void appendEvent(StringBuilder builder, Task task) {
        LocalDateTime start = task.getStartAt();
        if (start == null) {
            return;
        }
        LocalDateTime end = defaultEndAt(task, start);
        builder.append("BEGIN:VEVENT\r\n");
        builder.append("UID:").append(uid(task)).append("\r\n");
        builder.append("DTSTAMP:").append(formatUtc(task.getUpdatedAt() == null ? task.getCreatedAt() : task.getUpdatedAt())).append("\r\n");
        if (task.isAllDay()) {
            builder.append("DTSTART;VALUE=DATE:").append(DATE.format(start.toLocalDate())).append("\r\n");
            builder.append("DTEND;VALUE=DATE:").append(DATE.format(end.toLocalDate())).append("\r\n");
        } else {
            builder.append("DTSTART:").append(formatUtc(start)).append("\r\n");
            builder.append("DTEND:").append(formatUtc(end)).append("\r\n");
        }
        builder.append("SUMMARY:").append(escape(task.getTitle())).append("\r\n");
        builder.append("END:VEVENT\r\n");
    }

    private LocalDateTime defaultEndAt(Task task, LocalDateTime start) {
        if (task.getEndAt() != null) {
            return task.getEndAt();
        }
        return task.isAllDay() ? start.plusDays(1) : start.plusHours(1);
    }

    private String uid(Task task) {
        if (task.getRecurrenceSeries() != null && task.getOccurrenceDate() != null) {
            return "recurrence-%d-%s@todolab".formatted(task.getRecurrenceSeries().getId(), task.getOccurrenceDate());
        }
        return "task-%d@todolab".formatted(task.getId());
    }

    private String formatUtc(LocalDateTime value) {
        LocalDateTime dateTime = value == null ? LocalDateTime.now(Constant.ZONE) : value;
        return UTC_DATE_TIME.format(dateTime.atZone(Constant.ZONE).withZoneSameInstant(ZoneOffset.UTC));
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("\\", "\\\\")
                .replace(";", "\\;")
                .replace(",", "\\,")
                .replace("\r\n", "\\n")
                .replace("\n", "\\n");
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String rawToken) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm is not available", e);
        }
    }

    private Long ownerId(User owner) {
        if (owner == null || owner.getId() == null) {
            throw new IllegalArgumentException("owner는 영속화된 사용자여야 합니다.");
        }
        return owner.getId();
    }
}
