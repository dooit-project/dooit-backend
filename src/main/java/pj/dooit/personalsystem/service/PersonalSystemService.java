package pj.dooit.personalsystem.service;

import pj.dooit.Constant;
import pj.dooit.dday.repository.DdayGoalRepository;
import pj.dooit.personalsystem.domain.ActionVariant;
import pj.dooit.personalsystem.domain.NextActionPatchKind;
import pj.dooit.personalsystem.domain.PersonalSystem;
import pj.dooit.personalsystem.domain.PersonalSystemOccurrence;
import pj.dooit.personalsystem.domain.PersonalSystemOccurrenceStatus;
import pj.dooit.personalsystem.domain.PersonalSystemPracticeResult;
import pj.dooit.personalsystem.domain.PersonalSystemStatus;
import pj.dooit.personalsystem.domain.PersonalSystemVersion;
import pj.dooit.personalsystem.domain.ScheduleDay;
import pj.dooit.personalsystem.dto.PersonalSystemCreateRequest;
import pj.dooit.personalsystem.dto.PersonalSystemEvidenceResponse;
import pj.dooit.personalsystem.dto.PersonalSystemOccurrenceResponse;
import pj.dooit.personalsystem.dto.PersonalSystemPracticeResultRequest;
import pj.dooit.personalsystem.dto.PersonalSystemPracticeResultResponse;
import pj.dooit.personalsystem.dto.PersonalSystemResponse;
import pj.dooit.personalsystem.dto.PersonalSystemScheduleRequest;
import pj.dooit.personalsystem.dto.PersonalSystemStatusRequest;
import pj.dooit.personalsystem.dto.PersonalSystemUpdateRequest;
import pj.dooit.personalsystem.exception.PersonalSystemConflictException;
import pj.dooit.personalsystem.exception.PersonalSystemNotFoundException;
import pj.dooit.personalsystem.exception.PersonalSystemOccurrenceNotFoundException;
import pj.dooit.personalsystem.exception.PersonalSystemValidationException;
import pj.dooit.personalsystem.repository.PersonalSystemOccurrenceRepository;
import pj.dooit.personalsystem.repository.PersonalSystemPracticeResultRepository;
import pj.dooit.personalsystem.repository.PersonalSystemRepository;
import pj.dooit.personalsystem.repository.PersonalSystemVersionRepository;
import pj.dooit.task.domain.Task;
import pj.dooit.task.domain.TaskStatus;
import pj.dooit.task.domain.TaskType;
import pj.dooit.task.repository.TaskRepository;
import pj.dooit.user.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.zone.ZoneRulesException;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PersonalSystemService {

    private static final int MAX_RANGE_DAYS = 31;

    private final PersonalSystemRepository personalSystemRepository;
    private final PersonalSystemVersionRepository personalSystemVersionRepository;
    private final PersonalSystemOccurrenceRepository personalSystemOccurrenceRepository;
    private final PersonalSystemPracticeResultRepository personalSystemPracticeResultRepository;
    private final DdayGoalRepository ddayGoalRepository;
    private final TaskRepository taskRepository;

    @Transactional
    public PersonalSystemResponse create(PersonalSystemCreateRequest request, User owner) {
        Long ownerId = ownerId(owner);
        if (personalSystemRepository.countByOwnerIdAndStatus(ownerId, PersonalSystemStatus.ACTIVE) >= 1) {
            throw new PersonalSystemValidationException("첫 버전에서는 ACTIVE 시스템을 사용자당 1개만 만들 수 있습니다.");
        }
        validateLinkedDdayGoal(request.linkedDdayGoalId(), ownerId);
        PersonalSystem system = personalSystemRepository.save(new PersonalSystem(owner, request.linkedDdayGoalId()));
        PersonalSystemVersion version = personalSystemVersionRepository.save(toVersion(system, 1, request, request.schedule().startDate()));
        return PersonalSystemResponse.from(system, version);
    }

    @Transactional(readOnly = true)
    public List<PersonalSystemResponse> list(User owner) {
        Long ownerId = ownerId(owner);
        return personalSystemRepository.findByOwnerIdOrderByCreatedAtDescIdDesc(ownerId)
                .stream()
                .map(system -> PersonalSystemResponse.from(system, currentVersion(system)))
                .toList();
    }

    @Transactional(readOnly = true)
    public PersonalSystemResponse get(Long systemId, User owner) {
        PersonalSystem system = findSystem(systemId, ownerId(owner));
        return PersonalSystemResponse.from(system, currentVersion(system));
    }

    @Transactional
    public PersonalSystemResponse update(Long systemId, PersonalSystemUpdateRequest request, User owner) {
        Long ownerId = ownerId(owner);
        PersonalSystem system = findSystem(systemId, ownerId);
        if (system.getStatus() == PersonalSystemStatus.ARCHIVED) {
            throw new PersonalSystemValidationException("종료된 시스템은 수정할 수 없습니다.");
        }
        validateLinkedDdayGoal(request.linkedDdayGoalId(), ownerId);
        LocalDate effectiveFrom = request.effectiveFromDate() == null
                ? LocalDate.now(ZoneId.of(normalizeTimeZone(request.schedule())))
                : request.effectiveFromDate();
        system.advanceVersion(request.linkedDdayGoalId());
        PersonalSystemVersion version = personalSystemVersionRepository.save(toVersion(system, system.getCurrentVersion(), request, effectiveFrom));
        return PersonalSystemResponse.from(system, version);
    }

    @Transactional
    public PersonalSystemResponse changeStatus(Long systemId, PersonalSystemStatusRequest request, User owner) {
        Long ownerId = ownerId(owner);
        PersonalSystem system = findSystem(systemId, ownerId);
        if (request.status() == PersonalSystemStatus.ACTIVE) {
            if (system.getStatus() != PersonalSystemStatus.ACTIVE
                    && personalSystemRepository.countByOwnerIdAndStatus(ownerId, PersonalSystemStatus.ACTIVE) >= 1) {
                throw new PersonalSystemValidationException("첫 버전에서는 ACTIVE 시스템을 사용자당 1개만 유지할 수 있습니다.");
            }
            system.resume();
        } else if (request.status() == PersonalSystemStatus.PAUSED) {
            system.pause();
        } else if (request.status() == PersonalSystemStatus.ARCHIVED) {
            system.archive();
        }
        return PersonalSystemResponse.from(system, currentVersion(system));
    }

    @Transactional
    public List<PersonalSystemOccurrenceResponse> getOccurrences(LocalDate from, LocalDate to, User owner) {
        validateRange(from, to);
        Long ownerId = ownerId(owner);
        personalSystemRepository.findByOwnerIdOrderByCreatedAtDescIdDesc(ownerId)
                .stream()
                .filter(system -> system.getStatus() == PersonalSystemStatus.ACTIVE)
                .forEach(system -> materialize(system, owner, from, to));
        return occurrenceResponses(personalSystemOccurrenceRepository
                .findByOwnerIdAndServiceDateBetweenOrderByServiceDateAscIdAsc(ownerId, from, to));
    }

    @Transactional
    public PersonalSystemPracticeResultResponse putResult(Long occurrenceId, PersonalSystemPracticeResultRequest request, User owner) {
        PersonalSystemOccurrence occurrence = findOccurrence(occurrenceId, ownerId(owner));
        validateOccurrenceVersion(occurrence, request.occurrenceVersion());
        if (occurrence.getStatus() == PersonalSystemOccurrenceStatus.RESTED) {
            throw new PersonalSystemConflictException("휴식으로 기록된 회차는 실천 결과로 바로 덮어쓸 수 없습니다.");
        }
        String practicedTimeZone = request.practicedTimeZone() == null || request.practicedTimeZone().isBlank()
                ? occurrence.getTimeZone()
                : request.practicedTimeZone().trim();
        validateTimeZone(practicedTimeZone);
        validateNextActionPatch(request.nextActionPatchKind(), request.nextAction());

        PersonalSystemPracticeResult result = personalSystemPracticeResultRepository.findByOccurrenceId(occurrence.getId())
                .orElseGet(() -> new PersonalSystemPracticeResult(
                        occurrence,
                        request.actionVariant(),
                        request.practicedAt(),
                        practicedTimeZone,
                        normalizeNullable(request.accomplishedNote()),
                        request.difficulties(),
                        request.nextActionPatchKind(),
                        normalizeNullable(request.nextAction())
                ));
        result.replace(
                request.actionVariant(),
                request.practicedAt(),
                practicedTimeZone,
                normalizeNullable(request.accomplishedNote()),
                request.difficulties(),
                request.nextActionPatchKind(),
                normalizeNullable(request.nextAction())
        );
        occurrence.markPracticed();
        return PersonalSystemPracticeResultResponse.from(personalSystemPracticeResultRepository.save(result));
    }

    @Transactional
    public void deleteResult(Long occurrenceId, Long occurrenceVersion, User owner) {
        PersonalSystemOccurrence occurrence = findOccurrence(occurrenceId, ownerId(owner));
        validateOccurrenceVersion(occurrence, occurrenceVersion);
        personalSystemPracticeResultRepository.deleteById(occurrence.getId());
        occurrence.markPending();
    }

    @Transactional
    public PersonalSystemOccurrenceResponse rest(Long occurrenceId, Long occurrenceVersion, User owner) {
        PersonalSystemOccurrence occurrence = findOccurrence(occurrenceId, ownerId(owner));
        validateOccurrenceVersion(occurrence, occurrenceVersion);
        personalSystemPracticeResultRepository.deleteById(occurrence.getId());
        occurrence.markRested();
        return occurrenceResponses(List.of(occurrence)).getFirst();
    }

    @Transactional
    public void cancelRest(Long occurrenceId, Long occurrenceVersion, User owner) {
        PersonalSystemOccurrence occurrence = findOccurrence(occurrenceId, ownerId(owner));
        validateOccurrenceVersion(occurrence, occurrenceVersion);
        if (occurrence.getStatus() == PersonalSystemOccurrenceStatus.RESTED) {
            occurrence.markPending();
        }
    }

    @Transactional
    public PersonalSystemEvidenceResponse getEvidence(Long systemId, LocalDate from, LocalDate to, String timeZone, User owner) {
        validateRange(from, to);
        Long ownerId = ownerId(owner);
        PersonalSystem system = findSystem(systemId, ownerId);
        if (system.getStatus() == PersonalSystemStatus.ACTIVE) {
            materialize(system, owner, from, to);
        }
        List<PersonalSystemOccurrenceResponse> occurrences = occurrenceResponses(personalSystemOccurrenceRepository
                .findBySystemIdAndOwnerIdAndServiceDateBetweenOrderByServiceDateAscIdAsc(systemId, ownerId, from, to));
        Map<ActionVariant, Integer> countsByVariant = new EnumMap<>(ActionVariant.class);
        for (ActionVariant value : ActionVariant.values()) {
            countsByVariant.put(value, 0);
        }
        int practicedCount = 0;
        int restedCount = 0;
        int noRecordCount = 0;
        for (PersonalSystemOccurrenceResponse occurrence : occurrences) {
            if (occurrence.status() == PersonalSystemOccurrenceStatus.PRACTICED) {
                practicedCount++;
                ActionVariant variant = occurrence.result() == null ? ActionVariant.UNKNOWN : occurrence.result().actionVariant();
                countsByVariant.merge(variant, 1, Integer::sum);
            } else if (occurrence.status() == PersonalSystemOccurrenceStatus.RESTED) {
                restedCount++;
            } else {
                noRecordCount++;
            }
        }
        int practicedDateCount = (int) occurrences.stream()
                .filter(occurrence -> occurrence.status() == PersonalSystemOccurrenceStatus.PRACTICED)
                .map(PersonalSystemOccurrenceResponse::serviceDate)
                .distinct()
                .count();
        String effectiveTimeZone = timeZone == null || timeZone.isBlank() ? currentVersion(system).getScheduleTimeZone() : timeZone.trim();
        validateTimeZone(effectiveTimeZone);
        return new PersonalSystemEvidenceResponse(
                systemId,
                from,
                to,
                effectiveTimeZone,
                practicedCount,
                practicedDateCount,
                countsByVariant,
                restedCount,
                noRecordCount,
                occurrences
        );
    }

    private void materialize(PersonalSystem system, User owner, LocalDate from, LocalDate to) {
        List<PersonalSystemVersion> versions = personalSystemVersionRepository
                .findBySystemIdAndEffectiveFromDateLessThanEqualOrderByVersionAsc(system.getId(), to);
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            PersonalSystemVersion version = versionForDate(versions, date).orElse(null);
            if (version == null || !version.isScheduledOn(date)) {
                continue;
            }
            LocalDate serviceDate = date;
            PersonalSystemOccurrence occurrence = personalSystemOccurrenceRepository
                    .findBySystemIdAndOwnerIdAndServiceDate(system.getId(), owner.getId(), serviceDate)
                    .orElseGet(() -> personalSystemOccurrenceRepository.save(new PersonalSystemOccurrence(version, owner, serviceDate)));
            linkTaskIfNeeded(occurrence, version);
        }
    }

    private void linkTaskIfNeeded(PersonalSystemOccurrence occurrence, PersonalSystemVersion version) {
        if (occurrence.getLinkedTask() != null) {
            return;
        }
        LocalDate today = LocalDate.now(ZoneId.of(occurrence.getTimeZone()));
        if (occurrence.getServiceDate().isBefore(today)) {
            return;
        }
        LocalDateTime startAt = occurrence.getServiceDate().atStartOfDay();
        LocalDateTime endAt = occurrence.getServiceDate().plusDays(1).atStartOfDay();
        LocalDateTime notifyAt = version.getNotifyAtTime() == null
                ? null
                : LocalDateTime.of(occurrence.getServiceDate(), version.getNotifyAtTime());
        Task task = Task.builder()
                .title(truncate(firstNonBlank(occurrence.getSnapshotNextAction(), occurrence.getSnapshotMinimumActionText()), 255))
                .description(truncate(taskDescription(occurrence), 255))
                .type(TaskType.TODO)
                .startAt(startAt)
                .endAt(endAt)
                .allDay(true)
                .category("시스템")
                .estimatedDurationMinutes(null)
                .notificationEnabled(true)
                .notifyAt(notifyAt)
                .status(TaskStatus.TODAY)
                .targetDate(occurrence.getServiceDate())
                .owner(occurrence.getOwner())
                .build();
        occurrence.linkTask(taskRepository.save(task));
    }

    private String taskDescription(PersonalSystemOccurrence occurrence) {
        return "기본: %s\n최소: %s".formatted(
                occurrence.getSnapshotStandardActionText(),
                occurrence.getSnapshotMinimumActionText()
        );
    }

    private String firstNonBlank(String first, String fallback) {
        String normalized = normalizeNullable(first);
        return normalized == null ? fallback : normalized;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private Optional<PersonalSystemVersion> versionForDate(List<PersonalSystemVersion> versions, LocalDate date) {
        PersonalSystemVersion selected = null;
        for (PersonalSystemVersion version : versions) {
            if (!version.getEffectiveFromDate().isAfter(date)) {
                selected = version;
            }
        }
        return Optional.ofNullable(selected);
    }

    private List<PersonalSystemOccurrenceResponse> occurrenceResponses(List<PersonalSystemOccurrence> occurrences) {
        List<Long> occurrenceIds = occurrences.stream()
                .map(PersonalSystemOccurrence::getId)
                .filter(Objects::nonNull)
                .toList();
        Map<Long, PersonalSystemPracticeResult> resultsByOccurrenceId = occurrenceIds.isEmpty()
                ? new HashMap<>()
                : personalSystemPracticeResultRepository.findByOccurrenceIdIn(occurrenceIds)
                        .stream()
                        .collect(Collectors.toMap(PersonalSystemPracticeResult::getOccurrenceId, Function.identity()));
        return occurrences.stream()
                .map(occurrence -> PersonalSystemOccurrenceResponse.from(
                        occurrence,
                        PersonalSystemPracticeResultResponse.from(resultsByOccurrenceId.get(occurrence.getId()))
                ))
                .toList();
    }

    private PersonalSystem findSystem(Long systemId, Long ownerId) {
        return personalSystemRepository.findByIdAndOwnerId(systemId, ownerId)
                .orElseThrow(() -> new PersonalSystemNotFoundException("personalSystemId=" + systemId));
    }

    private PersonalSystemOccurrence findOccurrence(Long occurrenceId, Long ownerId) {
        return personalSystemOccurrenceRepository.findByIdAndOwnerId(occurrenceId, ownerId)
                .orElseThrow(() -> new PersonalSystemOccurrenceNotFoundException("occurrenceId=" + occurrenceId));
    }

    private PersonalSystemVersion currentVersion(PersonalSystem system) {
        return personalSystemVersionRepository.findBySystemIdAndVersion(system.getId(), system.getCurrentVersion())
                .orElseThrow(() -> new PersonalSystemNotFoundException("personalSystemVersion systemId=" + system.getId()));
    }

    private PersonalSystemVersion toVersion(PersonalSystem system, int version, PersonalSystemCreateRequest request, LocalDate effectiveFrom) {
        String timeZone = normalizeTimeZone(request.schedule());
        return new PersonalSystemVersion(
                system,
                version,
                normalizeRequired(request.directionText(), "directionText"),
                normalizeNullable(request.motivationText()),
                normalizeRequired(request.triggerText(), "triggerText"),
                request.schedule().startDate(),
                normalizedDays(request.schedule().days()),
                timeZone,
                request.schedule().notifyAtTime(),
                normalizeRequired(request.standardAction().text(), "standardAction.text"),
                normalizeNullable(request.standardAction().targetAmount()),
                request.standardAction().estimatedMinutes(),
                normalizeRequired(request.minimumAction().text(), "minimumAction.text"),
                normalizeNullable(request.minimumAction().targetAmount()),
                request.minimumAction().estimatedMinutes(),
                normalizeNullable(request.nextAction()),
                effectiveFrom
        );
    }

    private PersonalSystemVersion toVersion(PersonalSystem system, int version, PersonalSystemUpdateRequest request, LocalDate effectiveFrom) {
        String timeZone = normalizeTimeZone(request.schedule());
        return new PersonalSystemVersion(
                system,
                version,
                normalizeRequired(request.directionText(), "directionText"),
                normalizeNullable(request.motivationText()),
                normalizeRequired(request.triggerText(), "triggerText"),
                request.schedule().startDate(),
                normalizedDays(request.schedule().days()),
                timeZone,
                request.schedule().notifyAtTime(),
                normalizeRequired(request.standardAction().text(), "standardAction.text"),
                normalizeNullable(request.standardAction().targetAmount()),
                request.standardAction().estimatedMinutes(),
                normalizeRequired(request.minimumAction().text(), "minimumAction.text"),
                normalizeNullable(request.minimumAction().targetAmount()),
                request.minimumAction().estimatedMinutes(),
                normalizeNullable(request.nextAction()),
                effectiveFrom
        );
    }

    private void validateRange(LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from) || from.plusDays(MAX_RANGE_DAYS - 1L).isBefore(to)) {
            throw new PersonalSystemValidationException("조회 기간은 시작일과 종료일을 포함해 1일 이상 31일 이하이어야 합니다.");
        }
    }

    private void validateLinkedDdayGoal(Long ddayGoalId, Long ownerId) {
        if (ddayGoalId != null && !ddayGoalRepository.existsByIdAndOwnerId(ddayGoalId, ownerId)) {
            throw new PersonalSystemValidationException("linkedDdayGoalId는 본인 소유 D-Day만 사용할 수 있습니다.");
        }
    }

    private void validateOccurrenceVersion(PersonalSystemOccurrence occurrence, Long requestedVersion) {
        if (requestedVersion != null && requestedVersion != occurrence.getEntityVersion()) {
            throw new PersonalSystemConflictException("회차가 변경되었습니다. 새로고침 후 다시 시도해주세요.");
        }
    }

    private void validateNextActionPatch(NextActionPatchKind kind, String nextAction) {
        NextActionPatchKind effectiveKind = kind == null ? NextActionPatchKind.KEEP : kind;
        if (effectiveKind == NextActionPatchKind.REPLACE && normalizeNullable(nextAction) == null) {
            throw new PersonalSystemValidationException("nextActionPatchKind=REPLACE이면 nextAction이 필요합니다.");
        }
    }

    private List<ScheduleDay> normalizedDays(List<ScheduleDay> days) {
        if (days == null || days.isEmpty()) {
            throw new PersonalSystemValidationException("schedule.days는 필수입니다.");
        }
        return days.stream().distinct().toList();
    }

    private String normalizeTimeZone(PersonalSystemScheduleRequest schedule) {
        String timeZone = schedule.timeZone() == null || schedule.timeZone().isBlank()
                ? Constant.ZONE_ID
                : schedule.timeZone().trim();
        validateTimeZone(timeZone);
        return timeZone;
    }

    private void validateTimeZone(String timeZone) {
        try {
            ZoneId.of(timeZone);
        } catch (ZoneRulesException e) {
            throw new PersonalSystemValidationException("유효한 IANA timeZone이 아닙니다.");
        }
    }

    private String normalizeRequired(String value, String field) {
        String normalized = normalizeNullable(value);
        if (normalized == null) {
            throw new PersonalSystemValidationException(field + "는 필수입니다.");
        }
        return normalized;
    }

    private String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private Long ownerId(User owner) {
        if (owner == null || owner.getId() == null) {
            throw new IllegalArgumentException("owner는 영속화된 사용자여야 합니다.");
        }
        return owner.getId();
    }
}
