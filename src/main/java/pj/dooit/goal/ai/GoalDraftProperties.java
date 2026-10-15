package pj.dooit.goal.ai;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.goal-ai")
public class GoalDraftProperties {
    private boolean enabled = false;
}
