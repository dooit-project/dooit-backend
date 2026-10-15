package pj.dooit.goal.service;

import pj.dooit.Constant;
import pj.dooit.goal.domain.Goal;
import pj.dooit.goal.domain.GoalPlan;
import pj.dooit.goal.domain.GoalPlanStatus;
import pj.dooit.goal.domain.GoalPlanVersion;
import pj.dooit.goal.dto.GoalPlanCreateRequest;
import pj.dooit.goal.dto.GoalPlanResponse;
import pj.dooit.goal.dto.GoalPlanScheduleRequest;
import pj.dooit.goal.dto.GoalPlanStatusRequest;
import pj.dooit.goal.dto.GoalPlanUpdateRequest;
import pj.dooit.goal.exception.GoalNotFoundException;
import pj.dooit.goal.exception.GoalPlanNotFoundException;
import pj.dooit.goal.exception.GoalValidationException;
import pj.dooit.goal.repository.GoalPlanRepository;
import pj.dooit.goal.repository.GoalPlanVersionRepository;
import pj.dooit.goal.repository.GoalRepository;
import pj.dooit.user.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Service
@RequiredArgsConstructor
public class GoalPlanService {

    private final GoalPlanRepository goalPlanRepository;
    private final GoalPlanVersionRepository goalPlanVersionRepository;
    private final GoalRepository goalRepository;
    private final GoalOccurrenceTaskLinkService taskLinkService;

    @Transactional
    public GoalPlanResponse create(GoalPlanCreateRequest request, User owner) {
        Long ownerId = ownerId(owner);
        Goal goal = resolveGoal(request.goalId(), ownerId);
        GoalPlan plan = goalPlanRepository.save(new GoalPlan(owner, goal));
        GoalPlanVersion version = goalPlanVersionRepository.save(toVersion(
                plan,
                goal,
                request,
                request.schedule().startDate()
        ));
        return GoalPlanResponse.from(plan, version);
    }

    @Transactional(readOnly = true)
    public List<GoalPlanResponse> list(Long goalId, Boolean independentOnly, User owner) {
        Long ownerId = ownerId(owner);
        List<GoalPlan> plans;
        if (Boolean.TRUE.equals(independentOnly)) {
            plans = goalPlanRepository.findByOwnerIdAndGoalIsNullOrderByCreatedAtDescIdDesc(ownerId);
        } else if (goalId != null) {
            requireGoal(goalId, ownerId);
            plans = goalPlanRepository.findByOwnerIdAndGoalIdOrderByCreatedAtDescIdDesc(ownerId, goalId);
        } else {
            plans = goalPlanRepository.findByOwnerIdOrderByCreatedAtDescIdDesc(ownerId);
        }
        return plans.stream()
                .map(plan -> GoalPlanResponse.from(plan, currentVersion(plan)))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<GoalPlanResponse> listByGoal(Long goalId, User owner) {
        return list(goalId, false, owner);
    }

    @Transactional(readOnly = true)
    public GoalPlanResponse get(Long planId, User owner) {
        GoalPlan plan = findPlan(planId, ownerId(owner));
        return GoalPlanResponse.from(plan, currentVersion(plan));
    }

    @Transactional
    public GoalPlanResponse update(Long planId, GoalPlanUpdateRequest request, User owner) {
        Long ownerId = ownerId(owner);
        GoalPlan plan = findPlanForUpdate(planId, ownerId);
        if (plan.getStatus() == GoalPlanStatus.ARCHIVED) {
            throw new GoalValidationException("보관된 목표 계획은 수정할 수 없습니다.");
        }
        Goal goal = resolveGoal(request.goalId(), ownerId);
        LocalDate effectiveFrom = request.effectiveFromDate() == null
                ? LocalDate.now(Constant.ZONE)
                : request.effectiveFromDate();
        plan.advanceVersion(goal);
        GoalPlanVersion version = goalPlanVersionRepository.save(toVersion(plan, goal, request, effectiveFrom));
        return GoalPlanResponse.from(plan, version);
    }

    @Transactional
    public GoalPlanResponse changeStatus(Long planId, GoalPlanStatusRequest request, User owner) {
        GoalPlan plan = findPlanForUpdate(planId, ownerId(owner));
        if (request.status() == GoalPlanStatus.ACTIVE) {
            plan.activate();
        } else if (request.status() == GoalPlanStatus.PAUSED) {
            plan.pause();
        } else if (request.status() == GoalPlanStatus.ARCHIVED) {
            plan.archive();
        } else {
            throw new GoalValidationException("지원하지 않는 목표 계획 상태입니다.");
        }
        if (plan.getStatus() != GoalPlanStatus.ACTIVE) {
            taskLinkService.suspendFutureTasks(plan);
        }
        return GoalPlanResponse.from(plan, currentVersion(plan));
    }

    private GoalPlanVersion toVersion(
            GoalPlan plan,
            Goal goal,
            GoalPlanCreateRequest request,
            LocalDate effectiveFrom
    ) {
        String timeZone = normalizeTimeZone(request.schedule());
        return new GoalPlanVersion(
                plan,
                goal,
                request.name(),
                request.triggerText(),
                request.schedule().startDate(),
                request.schedule().days(),
                timeZone,
                request.schedule().notifyAtTime(),
                request.standardAction().text(),
                request.standardAction().targetAmount(),
                request.standardAction().estimatedMinutes(),
                request.minimumAction().text(),
                request.minimumAction().targetAmount(),
                request.minimumAction().estimatedMinutes(),
                request.nextAction(),
                effectiveFrom
        );
    }

    private GoalPlanVersion toVersion(
            GoalPlan plan,
            Goal goal,
            GoalPlanUpdateRequest request,
            LocalDate effectiveFrom
    ) {
        String timeZone = normalizeTimeZone(request.schedule());
        return new GoalPlanVersion(
                plan,
                goal,
                request.name(),
                request.triggerText(),
                request.schedule().startDate(),
                request.schedule().days(),
                timeZone,
                request.schedule().notifyAtTime(),
                request.standardAction().text(),
                request.standardAction().targetAmount(),
                request.standardAction().estimatedMinutes(),
                request.minimumAction().text(),
                request.minimumAction().targetAmount(),
                request.minimumAction().estimatedMinutes(),
                request.nextAction(),
                effectiveFrom
        );
    }

    private Goal resolveGoal(Long goalId, Long ownerId) {
        if (goalId == null) {
            return null;
        }
        return requireGoal(goalId, ownerId);
    }

    private Goal requireGoal(Long goalId, Long ownerId) {
        return goalRepository.findByIdAndOwnerId(goalId, ownerId)
                .orElseThrow(() -> new GoalNotFoundException("goalId=" + goalId));
    }

    private GoalPlan findPlanForUpdate(Long planId, Long ownerId) {
        return goalPlanRepository.findForUpdate(planId, ownerId)
                .orElseThrow(() -> new GoalPlanNotFoundException("planId=" + planId));
    }

    private GoalPlan findPlan(Long planId, Long ownerId) {
        return goalPlanRepository.findByIdAndOwnerId(planId, ownerId)
                .orElseThrow(() -> new GoalPlanNotFoundException("planId=" + planId));
    }

    private GoalPlanVersion currentVersion(GoalPlan plan) {
        return goalPlanVersionRepository.findByPlanIdAndVersion(plan.getId(), plan.getCurrentVersion())
                .orElseThrow(() -> new GoalPlanNotFoundException("goalPlanVersion planId=" + plan.getId()));
    }

    private String normalizeTimeZone(GoalPlanScheduleRequest schedule) {
        String timeZone = schedule.timeZone() == null || schedule.timeZone().isBlank()
                ? "Asia/Seoul"
                : schedule.timeZone().trim();
        try {
            ZoneId.of(timeZone);
        } catch (DateTimeException e) {
            throw new GoalValidationException("유효한 IANA timeZone이 아닙니다.");
        }
        return timeZone;
    }

    private Long ownerId(User owner) {
        if (owner == null || owner.getId() == null) {
            throw new IllegalArgumentException("owner는 영속화된 사용자여야 합니다.");
        }
        return owner.getId();
    }
}
