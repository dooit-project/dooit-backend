package pj.dooit.goal.service;

import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import pj.dooit.Constant;
import pj.dooit.goal.ai.GoalDraftProperties;
import pj.dooit.goal.ai.GoalPlanDraftProvider;
import pj.dooit.goal.dto.GoalPlanActionRequest;
import pj.dooit.goal.dto.GoalPlanDraftRequest;
import pj.dooit.goal.dto.GoalPlanDraftResponse;
import pj.dooit.goal.dto.GoalPlanScheduleRequest;
import pj.dooit.goal.dto.GoalResponse;
import pj.dooit.goal.exception.GoalAiUnavailableException;
import pj.dooit.goal.exception.GoalValidationException;
import pj.dooit.user.domain.User;
import java.time.DateTimeException;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
public class GoalPlanDraftService {
    private final GoalService goals;
    private final GoalDraftProperties properties;
    private final ObjectProvider<GoalPlanDraftProvider> providers;
    private final Validator validator;

    // GoalService returns an immutable owned snapshot in a short read transaction.
    // Provider calls run outside that transaction and never write planning entities.
    public GoalPlanDraftResponse draft(Long goalId, GoalPlanDraftRequest request, User owner) {
        GoalResponse goal = goals.get(goalId, owner);
        GoalPlanScheduleRequest schedule = normalizeSchedule(request.schedule());
        if (!properties.isEnabled()) throw new GoalAiUnavailableException();
        try {
            GoalPlanDraftProvider provider = providers.getIfAvailable();
            if (provider == null) throw new GoalAiUnavailableException();
            var context = new GoalPlanDraftProvider.Context(goal.title(), goal.desiredOutcome(),
                    goal.motivationText(), request.description().strip());
            var suggestion = provider.draft(context);
            if (suggestion == null || !validator.validate(suggestion).isEmpty()) {
                throw new GoalAiUnavailableException();
            }
            return new GoalPlanDraftResponse(goal.id(), goal.currentVersion(), suggestion.name().strip(),
                    normalize(suggestion.triggerText()), schedule, normalize(suggestion.standardAction()),
                    normalize(suggestion.minimumAction()), normalize(suggestion.nextAction()));
        } catch (RuntimeException ignored) {
            // Provider errors may contain prompts and credentials; discard message and cause.
            throw new GoalAiUnavailableException();
        }
    }

    private GoalPlanScheduleRequest normalizeSchedule(GoalPlanScheduleRequest schedule) {
        if (schedule.days().stream().anyMatch(java.util.Objects::isNull)) {
            throw new GoalValidationException("schedule.days에 null을 사용할 수 없습니다.");
        }
        String zone = normalize(schedule.timeZone());
        if (zone == null) zone = Constant.ZONE.getId();
        try {
            ZoneId.of(zone);
        } catch (DateTimeException ignored) {
            throw new GoalValidationException("유효한 IANA timeZone이 아닙니다.");
        }
        return new GoalPlanScheduleRequest(schedule.startDate(), schedule.days().stream().distinct().toList(),
                zone, schedule.notifyAtTime());
    }

    private GoalPlanActionRequest normalize(GoalPlanActionRequest action) {
        return new GoalPlanActionRequest(action.text().strip(), normalize(action.targetAmount()), action.estimatedMinutes());
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
