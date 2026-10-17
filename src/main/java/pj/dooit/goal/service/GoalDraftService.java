package pj.dooit.goal.service;

import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import pj.dooit.goal.ai.GoalDraftProperties;
import pj.dooit.goal.ai.GoalAiCallExecutor;
import pj.dooit.goal.exception.GoalAiRateLimitExceededException;
import pj.dooit.goal.ai.GoalDraftProvider;
import pj.dooit.goal.dto.GoalDraftRequest;
import pj.dooit.goal.dto.GoalDraftResponse;
import pj.dooit.goal.exception.GoalAiUnavailableException;

@Service
@RequiredArgsConstructor
public class GoalDraftService {
    private final GoalDraftProperties properties;
    private final GoalAiCallExecutor calls;
    private final ObjectProvider<GoalDraftProvider> providers;
    private final Validator validator;

    // No database transaction, persistence, or cache is involved in generating a draft.
    public GoalDraftResponse draft(GoalDraftRequest request, Long ownerId) {
        if (!properties.isEnabled()) throw new GoalAiUnavailableException();
        try {
            GoalDraftProvider provider = providers.getIfAvailable();
            if (provider == null) throw new GoalAiUnavailableException();
            GoalDraftProvider.Suggestion suggestion = calls.call(ownerId, () -> provider.draft(request.description().strip()));
            if (suggestion == null || !validator.validate(suggestion).isEmpty()) {
                throw new GoalAiUnavailableException();
            }
            return new GoalDraftResponse(suggestion.title().strip(), normalize(suggestion.desiredOutcome()),
                    normalize(suggestion.motivationText()), request.targetDate());
        } catch (GoalAiRateLimitExceededException e) {
            throw e;
        } catch (RuntimeException ignored) {
            // Provider errors can contain prompts, responses and credentials. Never retain their cause or message.
            throw new GoalAiUnavailableException();
        }
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
