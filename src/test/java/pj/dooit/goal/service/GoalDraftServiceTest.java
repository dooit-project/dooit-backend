package pj.dooit.goal.service;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import pj.dooit.goal.ai.GoalDraftProperties;
import pj.dooit.goal.ai.GoalDraftProvider;
import pj.dooit.goal.ai.GoalPlanDraftProvider;
import pj.dooit.goal.dto.GoalPlanDraftRequest;
import pj.dooit.goal.dto.GoalPlanScheduleRequest;
import pj.dooit.goal.dto.GoalResponse;
import pj.dooit.goal.domain.Goal;
import pj.dooit.goal.domain.ScheduleDay;
import pj.dooit.user.domain.User;
import java.time.LocalDate;
import java.util.List;
import static org.mockito.Mockito.*;
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

    @Test void planDraftWithoutAdapterStillFailsClosed() {
        var properties = new GoalDraftProperties();
        properties.setEnabled(true);
        var goals = mock(GoalService.class);
        var owner = new User("draft@example.com", "encoded", "사용자");
        when(goals.get(1L, owner)).thenReturn(GoalResponse.from(new Goal(owner, "목표", null, null, null, null)));
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var service = new GoalPlanDraftService(goals, properties,
                    new DefaultListableBeanFactory().getBeanProvider(GoalPlanDraftProvider.class), factory.getValidator());
            var request = new GoalPlanDraftRequest("계획",
                    new GoalPlanScheduleRequest(LocalDate.of(2026, 11, 1), List.of(ScheduleDay.MO), null, null));
            assertThatThrownBy(() -> service.draft(1L, request, owner))
                    .isInstanceOf(GoalAiUnavailableException.class).hasNoCause();
        }
    }
}
