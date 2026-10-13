package pj.dooit.goal.service;

import pj.dooit.Constant;
import pj.dooit.dday.repository.DdayGoalRepository;
import pj.dooit.goal.domain.Goal;
import pj.dooit.goal.domain.GoalFuturePlanPolicy;
import pj.dooit.goal.domain.GoalStatus;
import pj.dooit.goal.domain.GoalVersion;
import pj.dooit.goal.dto.GoalCreateRequest;
import pj.dooit.goal.dto.GoalFuturePlanPolicyRequest;
import pj.dooit.goal.dto.GoalResponse;
import pj.dooit.goal.dto.GoalStatusRequest;
import pj.dooit.goal.dto.GoalUpdateRequest;
import pj.dooit.goal.exception.GoalNotFoundException;
import pj.dooit.goal.exception.GoalValidationException;
import pj.dooit.goal.repository.GoalRepository;
import pj.dooit.goal.repository.GoalVersionRepository;
import pj.dooit.user.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class GoalService {

    private final GoalRepository goalRepository;
    private final GoalVersionRepository goalVersionRepository;
    private final DdayGoalRepository ddayGoalRepository;

    @Transactional
    public GoalResponse create(GoalCreateRequest request, User owner) {
        Long ownerId = ownerId(owner);
        validateLinkedDdayGoal(request.linkedDdayGoalId(), ownerId);
        Goal goal = goalRepository.save(new Goal(
                owner,
                request.title(),
                request.desiredOutcome(),
                request.motivationText(),
                request.targetDate(),
                request.linkedDdayGoalId()
        ));
        goalVersionRepository.save(new GoalVersion(goal, LocalDate.now(Constant.ZONE)));
        return GoalResponse.from(goal);
    }

    @Transactional(readOnly = true)
    public List<GoalResponse> list(User owner) {
        return goalRepository.findByOwnerIdOrderByCreatedAtDescIdDesc(ownerId(owner)).stream()
                .map(GoalResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public GoalResponse get(Long goalId, User owner) {
        return GoalResponse.from(findGoal(goalId, ownerId(owner)));
    }

    @Transactional
    public GoalResponse update(Long goalId, GoalUpdateRequest request, User owner) {
        Long ownerId = ownerId(owner);
        Goal goal = findGoal(goalId, ownerId);
        if (goal.getStatus() == GoalStatus.ARCHIVED) {
            throw new GoalValidationException("보관된 목표는 수정할 수 없습니다.");
        }
        validateLinkedDdayGoal(request.linkedDdayGoalId(), ownerId);
        LocalDate effectiveFrom = request.effectiveFromDate() == null
                ? LocalDate.now(Constant.ZONE)
                : request.effectiveFromDate();
        goal.update(
                request.title(),
                request.desiredOutcome(),
                request.motivationText(),
                request.targetDate(),
                request.linkedDdayGoalId()
        );
        goalVersionRepository.save(new GoalVersion(goal, effectiveFrom));
        return GoalResponse.from(goal);
    }

    @Transactional
    public GoalResponse changeStatus(Long goalId, GoalStatusRequest request, User owner) {
        Goal goal = findGoal(goalId, ownerId(owner));
        normalizePolicy(request.futurePlanPolicy());
        applyStatus(goal, request.status());
        return GoalResponse.from(goal);
    }

    @Transactional
    public GoalResponse achieve(Long goalId, GoalFuturePlanPolicyRequest request, User owner) {
        Goal goal = findGoal(goalId, ownerId(owner));
        normalizePolicy(request == null ? null : request.futurePlanPolicy());
        goal.achieve();
        return GoalResponse.from(goal);
    }

    @Transactional
    public GoalResponse cancelAchievement(Long goalId, User owner) {
        Goal goal = findGoal(goalId, ownerId(owner));
        goal.cancelAchievement();
        return GoalResponse.from(goal);
    }

    private void applyStatus(Goal goal, GoalStatus status) {
        if (status == GoalStatus.ACTIVE) {
            goal.activate();
        } else if (status == GoalStatus.PAUSED) {
            goal.pause();
        } else if (status == GoalStatus.ACHIEVED) {
            goal.achieve();
        } else if (status == GoalStatus.ARCHIVED) {
            goal.archive();
        } else {
            throw new GoalValidationException("지원하지 않는 목표 상태입니다.");
        }
    }

    private GoalFuturePlanPolicy normalizePolicy(GoalFuturePlanPolicy policy) {
        return policy == null ? GoalFuturePlanPolicy.PAUSE_FUTURE : policy;
    }

    private Goal findGoal(Long goalId, Long ownerId) {
        return goalRepository.findByIdAndOwnerId(goalId, ownerId)
                .orElseThrow(() -> new GoalNotFoundException("goalId=" + goalId));
    }

    private void validateLinkedDdayGoal(Long linkedDdayGoalId, Long ownerId) {
        if (linkedDdayGoalId == null) {
            return;
        }
        if (!ddayGoalRepository.existsByIdAndOwnerId(linkedDdayGoalId, ownerId)) {
            throw new GoalValidationException("linkedDdayGoalId는 본인 소유 D-Day만 사용할 수 있습니다.");
        }
    }

    private Long ownerId(User owner) {
        if (owner == null || owner.getId() == null) {
            throw new IllegalArgumentException("owner는 영속화된 사용자여야 합니다.");
        }
        return owner.getId();
    }
}
