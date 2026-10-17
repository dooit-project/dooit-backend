package pj.dooit.goal.exception;

import lombok.Getter;

@Getter
public class GoalAiRateLimitExceededException extends RuntimeException {
    private final long retryAfterSeconds;

    public GoalAiRateLimitExceededException(long retryAfterSeconds) {
        super("Goal AI rate limit exceeded");
        this.retryAfterSeconds = retryAfterSeconds;
    }
}
