package pj.dooit.goal.service;

import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import pj.dooit.goal.ai.GoalAdjustmentDraftProvider;
import pj.dooit.goal.ai.GoalDraftProperties;
import pj.dooit.goal.ai.GoalAiCallExecutor;
import pj.dooit.goal.exception.GoalAiRateLimitExceededException;
import pj.dooit.goal.dto.GoalAdjustmentDraftRequest;
import pj.dooit.goal.dto.GoalAdjustmentDraftResponse;
import pj.dooit.goal.exception.GoalAiUnavailableException;
import pj.dooit.user.domain.User;

@Service
@RequiredArgsConstructor
public class GoalAdjustmentDraftService {
    private final GoalAdjustmentSnapshotService snapshots;
    private final GoalDraftProperties properties;
    private final GoalAiCallExecutor calls;
    private final ObjectProvider<GoalAdjustmentDraftProvider> providers;
    private final Validator validator;

    // Snapshot read transaction ends before provider execution. Nothing is written or cached.
    public GoalAdjustmentDraftResponse draft(Long goalId, GoalAdjustmentDraftRequest request, User owner) {
        var snapshot = snapshots.read(goalId, request.planId(), owner);
        var goal = snapshot.goal();
        var plan = snapshot.plan();
        if (!properties.isEnabled()) throw new GoalAiUnavailableException();
        try {
            var provider = providers.getIfAvailable();
            if (provider == null) throw new GoalAiUnavailableException();
            var context = new GoalAdjustmentDraftProvider.Context(goal.title(), goal.desiredOutcome(), goal.motivationText(),
                    plan.name(), plan.triggerText(), plan.standardAction(), plan.minimumAction(), plan.nextAction(),
                    request.description().strip());
            var suggestion = GoalPlanDraftContent.validated(calls.call(owner.getId(), () -> provider.draft(context)), validator);
            return new GoalAdjustmentDraftResponse(goal.id(), goal.currentVersion(), plan.id(), plan.currentVersion(),
                    suggestion.name(), suggestion.triggerText(), plan.schedule(), suggestion.standardAction(),
                    suggestion.minimumAction(), suggestion.nextAction());
        } catch (GoalAiRateLimitExceededException e) {
            throw e;
        } catch (RuntimeException ignored) {
            // Never expose provider prompt/response/credential details through exception messages or causes.
            throw new GoalAiUnavailableException();
        }
    }
}
