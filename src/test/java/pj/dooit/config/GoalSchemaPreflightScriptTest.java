package pj.dooit.config;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.assertThat;

class GoalSchemaPreflightScriptTest {
    @Test void readOnlyPreflightAndReleaseGateContracts() throws Exception {
        Process process = new ProcessBuilder("python3", "-B", "-m", "unittest", "discover", "-s", "src/test/python",
                "-p", "test_goal_schema_preflight.py").redirectErrorStream(true).start();
        boolean finished = process.waitFor(20, TimeUnit.SECONDS);
        if (!finished) process.destroyForcibly();
        assertThat(finished).as("Schema preflight tests timed out").isTrue();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(process.exitValue()).as(output).isZero();
    }
}
