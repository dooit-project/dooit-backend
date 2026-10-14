package pj.dooit.goal.service;

import pj.dooit.goal.domain.GoalPlan;
import pj.dooit.goal.domain.GoalPlanVersion;
import pj.dooit.goal.domain.GoalPlanStatus;
import pj.dooit.goal.domain.GoalPlanOccurrence;
import pj.dooit.goal.domain.GoalOccurrenceStatus;
import pj.dooit.goal.domain.GoalVersion;
import pj.dooit.goal.dto.GoalPlanOccurrenceResponse;
import pj.dooit.goal.exception.GoalNotFoundException;
import pj.dooit.goal.exception.GoalValidationException;
import pj.dooit.goal.repository.GoalPlanRepository;
import pj.dooit.goal.repository.GoalPlanVersionRepository;
import pj.dooit.goal.repository.GoalVersionRepository;
import pj.dooit.goal.repository.GoalRepository;
import pj.dooit.goal.repository.GoalPlanOccurrenceRepository;
import pj.dooit.user.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class GoalPlanOccurrenceService {
    private final GoalPlanRepository planRepository;
    private final GoalPlanVersionRepository planVersionRepository;
    private final GoalVersionRepository goalVersionRepository;
    private final GoalRepository goalRepository;
    private final GoalPlanOccurrenceRepository occurrenceRepository;

    @Transactional
    public List<GoalPlanOccurrenceResponse> getOccurrences(LocalDate from, LocalDate to, Long goalId, User owner) {
        if (from == null || to == null || to.isBefore(from) || ChronoUnit.DAYS.between(from, to) >= 31) {
            throw new GoalValidationException("조회 기간은 시작일과 종료일을 포함해 1일 이상 31일 이하이어야 합니다.");
        }
        List<GoalPlan> plans = planRepository.findAllForMaterialization(owner.getId());
        if (goalId != null && !goalRepository.existsByIdAndOwnerId(goalId, owner.getId())) {
            throw new GoalNotFoundException("goalId=" + goalId);
        }
        List<GoalPlanOccurrence> selected = new ArrayList<>();
        // Lock plans in ID order: simultaneous range queries cannot create duplicate dates.
        for (GoalPlan plan : plans) {
            List<GoalPlanVersion> versions = planVersionRepository
                    .findByPlanIdAndEffectiveFromDateLessThanEqualOrderByVersionAsc(plan.getId(), to);
            Map<LocalDate, GoalPlanOccurrence> existing = new HashMap<>();
            occurrenceRepository.findByPlanIdAndServiceDateBetweenOrderByServiceDateAscIdAsc(plan.getId(), from, to)
                    .forEach(o -> existing.put(o.getServiceDate(), o));
            Map<Long, List<GoalVersion>> goalVersions = new HashMap<>();
            for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
                GoalPlanVersion version = versionForDate(versions, date);
                GoalPlanOccurrence occurrence = existing.get(date);
                boolean active = plan.getStatus() == GoalPlanStatus.ACTIVE;
                boolean scheduled = version != null && version.isScheduledOn(date);
                boolean future = occurrence != null && !date.isBefore(LocalDate.now(ZoneId.of(occurrence.getTimeZone())));
                boolean mutable = occurrence != null && future
                        && occurrence.getStatus() == GoalOccurrenceStatus.PENDING && occurrence.getLinkedTaskId() == null;
                if (occurrence == null && active && scheduled) {
                    occurrence = occurrenceRepository.save(new GoalPlanOccurrence(
                            version, goalVersionForDate(version, date, to, goalVersions), owner, date));
                } else if (mutable && active && scheduled) {
                    occurrence.refreshSnapshot(version, goalVersionForDate(version, date, to, goalVersions));
                }
                // Retain historical/result-bearing snapshots, but hide unscheduled pending future dates.
                if (occurrence == null || (mutable && (!active || !scheduled))) {
                    continue;
                }
                if (goalId == null || goalId.equals(occurrence.getGoalId())) {
                    selected.add(occurrence);
                }
            }
        }
        occurrenceRepository.flush();
        return selected.stream()
                .sorted(Comparator.comparing(GoalPlanOccurrence::getServiceDate)
                        .thenComparing(GoalPlanOccurrence::getId))
                .map(o -> GoalPlanOccurrenceResponse.from(o, o.getPlan().getStatus() == GoalPlanStatus.ACTIVE))
                .toList();
    }

    private GoalPlanVersion versionForDate(List<GoalPlanVersion> versions, LocalDate date) {
        GoalPlanVersion selected = null;
        for (GoalPlanVersion version : versions) {
            if (!version.getEffectiveFromDate().isAfter(date)) {
                selected = version;
            }
        }
        return selected;
    }

    private GoalVersion goalVersionForDate(GoalPlanVersion planVersion, LocalDate date, LocalDate to,
                                           Map<Long, List<GoalVersion>> cache) {
        if (planVersion.getGoal() == null) {
            return null;
        }
        List<GoalVersion> versions = cache.computeIfAbsent(planVersion.getGoal().getId(), id ->
                goalVersionRepository.findByGoalIdAndEffectiveFromDateLessThanEqualOrderByVersionAsc(id, to));
        GoalVersion selected = null;
        for (GoalVersion version : versions) {
            if (!version.getEffectiveFromDate().isAfter(date)) {
                selected = version;
            }
        }
        // A plan can start before the goal was created; use its original goal snapshot then.
        return selected != null ? selected : goalVersionRepository
                .findByGoalIdAndVersion(planVersion.getGoal().getId(), 1).orElseThrow();
    }
}
