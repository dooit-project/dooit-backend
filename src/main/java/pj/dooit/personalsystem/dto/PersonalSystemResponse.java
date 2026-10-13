package pj.dooit.personalsystem.dto;

import pj.dooit.personalsystem.domain.PersonalSystem;
import pj.dooit.personalsystem.domain.PersonalSystemStatus;
import pj.dooit.personalsystem.domain.PersonalSystemVersion;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record PersonalSystemResponse(
        Long id,
        int version,
        PersonalSystemStatus status,
        String directionText,
        String motivationText,
        String triggerText,
        PersonalSystemScheduleResponse schedule,
        PersonalSystemActionResponse standardAction,
        PersonalSystemActionResponse minimumAction,
        String nextAction,
        Long linkedDdayGoalId,
        LocalDate effectiveFromDate,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static PersonalSystemResponse from(PersonalSystem system, PersonalSystemVersion version) {
        return new PersonalSystemResponse(
                system.getId(),
                version.getVersion(),
                system.getStatus(),
                version.getDirectionText(),
                version.getMotivationText(),
                version.getTriggerText(),
                PersonalSystemScheduleResponse.from(version),
                new PersonalSystemActionResponse(
                        version.getStandardActionText(),
                        version.getStandardTargetAmount(),
                        version.getStandardEstimatedMinutes()
                ),
                new PersonalSystemActionResponse(
                        version.getMinimumActionText(),
                        version.getMinimumTargetAmount(),
                        version.getMinimumEstimatedMinutes()
                ),
                version.getNextAction(),
                system.getLinkedDdayGoalId(),
                version.getEffectiveFromDate(),
                system.getCreatedAt(),
                system.getUpdatedAt()
        );
    }
}
