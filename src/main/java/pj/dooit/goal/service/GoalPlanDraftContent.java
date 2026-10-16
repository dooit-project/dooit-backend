package pj.dooit.goal.service;

import jakarta.validation.Validator;
import pj.dooit.goal.ai.GoalPlanDraftProvider;
import pj.dooit.goal.dto.GoalPlanActionRequest;
import pj.dooit.goal.exception.GoalAiUnavailableException;

/** Both new-plan and adjustment suggestions must satisfy the same create/update constraints. */
final class GoalPlanDraftContent {
    private GoalPlanDraftContent() {}

    static GoalPlanDraftProvider.Suggestion validated(GoalPlanDraftProvider.Suggestion suggestion, Validator validator) {
        if (suggestion == null || !validator.validate(suggestion).isEmpty()) throw new GoalAiUnavailableException();
        return new GoalPlanDraftProvider.Suggestion(suggestion.name().strip(), normalize(suggestion.triggerText()),
                normalize(suggestion.standardAction()), normalize(suggestion.minimumAction()), normalize(suggestion.nextAction()));
    }

    private static GoalPlanActionRequest normalize(GoalPlanActionRequest action) {
        return new GoalPlanActionRequest(action.text().strip(), normalize(action.targetAmount()), action.estimatedMinutes());
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
