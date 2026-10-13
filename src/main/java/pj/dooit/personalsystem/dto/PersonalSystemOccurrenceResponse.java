package pj.dooit.personalsystem.dto;

import pj.dooit.personalsystem.domain.PersonalSystemOccurrence;
import pj.dooit.personalsystem.domain.PersonalSystemOccurrenceStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public record PersonalSystemOccurrenceResponse(
        Long id,
        Long systemId,
        int systemVersion,
        LocalDate serviceDate,
        String timeZone,
        PersonalSystemOccurrenceStatus status,
        Long linkedTaskId,
        String notificationKey,
        LocalDateTime scheduledNotificationAt,
        String snapshotDirectionText,
        String snapshotTriggerText,
        String snapshotStandardActionText,
        String snapshotMinimumActionText,
        String snapshotNextAction,
        long occurrenceVersion,
        PersonalSystemPracticeResultResponse result
) {
    public static PersonalSystemOccurrenceResponse from(
            PersonalSystemOccurrence occurrence,
            PersonalSystemPracticeResultResponse result
    ) {
        return new PersonalSystemOccurrenceResponse(
                occurrence.getId(),
                occurrence.getSystem().getId(),
                occurrence.getSystemVersion(),
                occurrence.getServiceDate(),
                occurrence.getTimeZone(),
                occurrence.getStatus(),
                occurrence.getLinkedTask() == null ? null : occurrence.getLinkedTask().getId(),
                notificationKey(occurrence),
                scheduledNotificationAt(occurrence.getServiceDate(), occurrence.getSnapshotNotifyAtTime()),
                occurrence.getSnapshotDirectionText(),
                occurrence.getSnapshotTriggerText(),
                occurrence.getSnapshotStandardActionText(),
                occurrence.getSnapshotMinimumActionText(),
                occurrence.getSnapshotNextAction(),
                occurrence.getEntityVersion(),
                result
        );
    }

    private static String notificationKey(PersonalSystemOccurrence occurrence) {
        return "personal-system:%d:%s".formatted(occurrence.getSystem().getId(), occurrence.getServiceDate());
    }

    private static LocalDateTime scheduledNotificationAt(LocalDate serviceDate, LocalTime notifyAtTime) {
        return notifyAtTime == null ? null : LocalDateTime.of(serviceDate, notifyAtTime);
    }
}
