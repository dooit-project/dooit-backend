package pj.dooit.personalsystem.service;

import pj.dooit.Constant;
import pj.dooit.auth.exception.GuestSessionExpiredException;
import pj.dooit.dday.repository.DdayGoalRepository;
import pj.dooit.goal.domain.ActionVariant;
import pj.dooit.goal.domain.Goal;
import pj.dooit.goal.domain.GoalOccurrenceStatus;
import pj.dooit.goal.domain.GoalPlan;
import pj.dooit.goal.domain.GoalPlanOccurrence;
import pj.dooit.goal.domain.GoalPlanVersion;
import pj.dooit.goal.domain.GoalPracticeResult;
import pj.dooit.goal.domain.GoalVersion;
import pj.dooit.goal.domain.NextActionPatchKind;
import pj.dooit.goal.domain.PracticeDifficulty;
import pj.dooit.goal.domain.ScheduleDay;
import pj.dooit.goal.repository.GoalRepository;
import pj.dooit.goal.repository.GoalVersionRepository;
import pj.dooit.goal.repository.GoalPlanRepository;
import pj.dooit.goal.repository.GoalPlanVersionRepository;
import pj.dooit.goal.repository.GoalPlanOccurrenceRepository;
import pj.dooit.goal.repository.GoalPracticeResultRepository;
import pj.dooit.goal.service.GoalOccurrenceTaskLinkService;
import pj.dooit.personalsystem.domain.PersonalSystemOccurrenceStatus;
import pj.dooit.personalsystem.domain.PersonalSystemStatus;
import pj.dooit.personalsystem.dto.PersonalSystemMigrationResponse;
import pj.dooit.personalsystem.exception.PersonalSystemConflictException;
import pj.dooit.personalsystem.exception.PersonalSystemNotFoundException;
import pj.dooit.personalsystem.repository.PersonalSystemRepository;
import pj.dooit.personalsystem.repository.PersonalSystemVersionRepository;
import pj.dooit.personalsystem.repository.PersonalSystemOccurrenceRepository;
import pj.dooit.personalsystem.repository.PersonalSystemPracticeResultRepository;
import pj.dooit.user.domain.AccountType;
import pj.dooit.user.domain.User;
import pj.dooit.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PersonalSystemMigrationService {
    private final UserRepository userRepository;
    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;
    private final PersonalSystemRepository systemRepository;
    private final PersonalSystemVersionRepository versionRepository;
    private final PersonalSystemOccurrenceRepository occurrenceRepository;
    private final PersonalSystemPracticeResultRepository resultRepository;
    private final GoalRepository goalRepository;
    private final GoalVersionRepository goalVersionRepository;
    private final GoalPlanRepository planRepository;
    private final GoalPlanVersionRepository planVersionRepository;
    private final GoalPlanOccurrenceRepository goalOccurrenceRepository;
    private final GoalPracticeResultRepository goalResultRepository;
    private final GoalOccurrenceTaskLinkService taskLinkService;
    private final DdayGoalRepository ddayRepository;

    @Transactional
    public PersonalSystemMigrationResponse migrate(Long systemId, User owner) {
        User lockedOwner = userRepository.findWithLockById(owner.getId()).orElseThrow(GuestSessionExpiredException::new);
        entityManager.refresh(lockedOwner, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        if (lockedOwner.getMergedIntoUserId() != null || (lockedOwner.getAccountType() == AccountType.GUEST
                && (lockedOwner.getGuestExpiresAt() == null || lockedOwner.getGuestExpiresAt().isBefore(LocalDateTime.now(Constant.ZONE))))) {
            throw new GuestSessionExpiredException();
        }
        var source = systemRepository.findLockedByIdAndOwnerId(systemId, owner.getId())
                .orElseThrow(() -> new PersonalSystemNotFoundException("systemId=" + systemId));
        var versions = versionRepository.findBySystemIdOrderByVersionAsc(systemId);
        var occurrences = occurrenceRepository.findBySystemIdOrderByIdAsc(systemId);
        // Current locking reads also include edits committed while waiting for occurrence locks.
        var results = resultRepository.findLockedByOccurrenceIdIn(occurrences.stream().map(o -> o.getId()).toList());
        if (source.getMigratedPlanId() != null) {
            return new PersonalSystemMigrationResponse(systemId, source.getMigratedGoalId(), source.getMigratedPlanId(),
                    versions.size(), occurrences.size(), results.size(), true);
        }
        if (versions.isEmpty() || versions.getFirst().getVersion() != 1
                || versions.getLast().getVersion() != source.getCurrentVersion()
                || versions.size() != source.getCurrentVersion()) {
            throw new PersonalSystemConflictException("이관할 시스템 version 이력을 확인해주세요.");
        }
        if (source.getLinkedDdayGoalId() != null
                && !ddayRepository.existsByIdAndOwnerId(source.getLinkedDdayGoalId(), owner.getId())) {
            throw new PersonalSystemConflictException("이관할 D-Day의 소유권을 확인해주세요.");
        }
        PersonalSystemStatus originalStatus = source.getStatus();
        var first = versions.getFirst();
        Goal goal = goalRepository.save(new Goal(lockedOwner, title(first.getDirectionText()), first.getDirectionText(),
                first.getMotivationText(), null, source.getLinkedDdayGoalId()));
        GoalPlan plan = planRepository.save(new GoalPlan(lockedOwner, goal));
        Map<Integer, GoalVersion> goalVersions = new HashMap<>();
        Map<Integer, GoalPlanVersion> planVersions = new HashMap<>();
        for (var version : versions) {
            if (version.getVersion() > 1) {
                goal.update(title(version.getDirectionText()), version.getDirectionText(), version.getMotivationText(),
                        null, source.getLinkedDdayGoalId());
                plan.advanceVersion(goal);
            }
            goalVersions.put(version.getVersion(), goalVersionRepository.save(new GoalVersion(goal, version.getEffectiveFromDate())));
            planVersions.put(version.getVersion(), planVersionRepository.save(new GoalPlanVersion(plan, goal,
                    title(version.getDirectionText()), version.getTriggerText(), version.getScheduleStartDate(),
                    version.scheduleDays().stream().map(day -> ScheduleDay.valueOf(day.name())).toList(),
                    version.getScheduleTimeZone(), version.getNotifyAtTime(), version.getStandardActionText(),
                    version.getStandardTargetAmount(), version.getStandardEstimatedMinutes(), version.getMinimumActionText(),
                    version.getMinimumTargetAmount(), version.getMinimumEstimatedMinutes(), version.getNextAction(), version.getEffectiveFromDate())));
        }
        var resultsById = results.stream().collect(Collectors.toMap(r -> r.getOccurrenceId(), Function.identity()));
        for (var old : occurrences) {
            var result = resultsById.get(old.getId());
            if ((old.getStatus() == PersonalSystemOccurrenceStatus.PRACTICED) != (result != null)
                    || !planVersions.containsKey(old.getSystemVersion()) || !old.getOwner().getId().equals(owner.getId())
                    || (old.getLinkedTask() != null && (old.getLinkedTask().getOwner() == null
                    || !owner.getId().equals(old.getLinkedTask().getOwner().getId())
                    || (old.getStatus() == PersonalSystemOccurrenceStatus.PENDING && old.getLinkedTask().getCompletedAt() != null)))) {
                throw new PersonalSystemConflictException("이관할 회차 결과와 소유권을 확인해주세요.");
            }
            GoalPlanOccurrence occurrence = new GoalPlanOccurrence(planVersions.get(old.getSystemVersion()),
                    goalVersions.get(old.getSystemVersion()), lockedOwner, old.getServiceDate());
            occurrence.restoreImportedSnapshot(old.getTimeZone(), title(old.getSnapshotDirectionText()),
                    title(old.getSnapshotDirectionText()), old.getSnapshotTriggerText(), old.getSnapshotStandardActionText(),
                    old.getSnapshotMinimumActionText(), old.getSnapshotNextAction(), old.getSnapshotNotifyAtTime(),
                    GoalOccurrenceStatus.valueOf(old.getStatus().name()), old.getLinkedTask() == null ? null : old.getLinkedTask().getId());
            goalOccurrenceRepository.save(occurrence);
            if (result != null) {
                goalResultRepository.save(new GoalPracticeResult(occurrence, ActionVariant.valueOf(result.getActionVariant().name()),
                        result.getPracticedAt(), result.getPracticedTimeZone(), result.getAccomplishedNote(),
                        result.difficulties().stream().map(d -> PracticeDifficulty.valueOf(d.name())).toList(),
                        NextActionPatchKind.valueOf(result.getNextActionPatchKind().name()), result.getNextActionValue()));
            }
            if (old.getLinkedTask() != null) {
                old.getLinkedTask().disableNotification();
                if (result != null) {
                    taskLinkService.completeLinkedTask(occurrence, result.getPracticedAt(), result.getPracticedTimeZone());
                } else if (old.getStatus() == PersonalSystemOccurrenceStatus.RESTED || (originalStatus != PersonalSystemStatus.ACTIVE
                        && !old.getServiceDate().isBefore(LocalDate.now(ZoneId.of(old.getTimeZone()))))) {
                    taskLinkService.resetLinkedTask(occurrence, true);
                }
            }
            old.markMigrated(occurrence.getId());
        }
        if (originalStatus == PersonalSystemStatus.PAUSED) {
            goal.pause();
            plan.pause();
        } else if (originalStatus == PersonalSystemStatus.ARCHIVED) {
            goal.archive();
            plan.archive();
        }
        source.markMigrated(goal.getId(), plan.getId());
        systemRepository.flush();
        return new PersonalSystemMigrationResponse(systemId, goal.getId(), plan.getId(), versions.size(), occurrences.size(), results.size(), false);
    }

    private String title(String value) {
        int end = Math.min(value.length(), 120);
        if (end < value.length() && Character.isHighSurrogate(value.charAt(end - 1))
                && Character.isLowSurrogate(value.charAt(end))) {
            end--;
        }
        return value.substring(0, end).trim();
    }
}
