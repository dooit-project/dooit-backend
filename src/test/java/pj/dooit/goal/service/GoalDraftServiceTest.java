package pj.dooit.goal.service;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import pj.dooit.goal.ai.GoalDraftProperties;
import pj.dooit.goal.ai.GoalDraftProvider;
import pj.dooit.goal.dto.GoalDraftRequest;
import pj.dooit.goal.exception.GoalAiUnavailableException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GoalDraftServiceTest {
    @Test void enabledWithoutAdapterStillFailsClosed() {
        var properties = new GoalDraftProperties();
        properties.setEnabled(true);
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var service = new GoalDraftService(properties,
                    new DefaultListableBeanFactory().getBeanProvider(GoalDraftProvider.class), factory.getValidator());
            assertThatThrownBy(() -> service.draft(new GoalDraftRequest("목표", null)))
                    .isInstanceOf(GoalAiUnavailableException.class).hasNoCause();
        }
    }
}
