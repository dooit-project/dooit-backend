package pj.dooit.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.assertThat;

class ProductionRecoveryMetadataTest {
    @TempDir Path temporary;

    @Test void recoveryRetainsRunningImagesCommitInsteadOfDefaultingToLocal() throws Exception {
        assertRecovery("preserved-running-commit", "", "preserved-running-commit");
    }

    @Test void recoveryRepairsLocalCommitUsingTheRunningImageTagRatherThanHead() throws Exception {
        assertRecovery("local", "", "resolved-running-image-commit");
    }

    @Test void explicitDeploymentCommitTakesPrecedenceOverOldContainerMetadata() throws Exception {
        assertRecovery("old-container-commit", "explicit-deployment-commit", "explicit-deployment-commit");
    }

    @Test void explicitNewImageDoesNotInheritOldContainersCommit() throws Exception {
        assertRecovery("old-container-commit", "", "resolved-new-image-commit", "new-release", "new-release");
    }

    private void assertRecovery(String existingCommit, String explicitCommit, String expected) throws Exception {
        assertRecovery(existingCommit, explicitCommit, expected, "", "6cdc9e5");
    }

    private void assertRecovery(String existingCommit, String explicitCommit, String expected, String imageTag, String expectedTag) throws Exception {
        Path bin = Files.createDirectory(temporary.resolve("bin"));
        Path result = temporary.resolve("compose-env.txt");
        Path docker = bin.resolve("docker");
        Files.writeString(docker, """
                #!/bin/bash
                case "$1" in
                  desktop|info) exit 0 ;;
                  inspect)
                    case "$*" in
                      *Config.Image*) echo 'dooit-backend:6cdc9e5' ;;
                      *Config.Env*) echo "DOOIT_APP_COMMIT_SHA=$DOOIT_TEST_EXISTING_COMMIT" ;;
                    esac
                    exit 0 ;;
                  compose)
                    printf '%s\\n%s\\n' "$DOOIT_APP_IMAGE_TAG" "$DOOIT_APP_COMMIT_SHA" > "$DOOIT_TEST_OUTPUT"
                    exit 0 ;;
                esac
                exit 1
                """);
        Path curl = bin.resolve("curl");
        Files.writeString(curl, "#!/bin/bash\nexit 0\n");
        Path git = bin.resolve("git");
        Files.writeString(git, """
                #!/bin/bash
                case "$*" in
                  'rev-parse --verify 6cdc9e5^{commit}') echo resolved-running-image-commit ;;
                  'rev-parse --verify new-release^{commit}') echo resolved-new-image-commit ;;
                  *) exit 1 ;;
                esac
                """);
        assertThat(docker.toFile().setExecutable(true)).isTrue();
        assertThat(curl.toFile().setExecutable(true)).isTrue();
        assertThat(git.toFile().setExecutable(true)).isTrue();
        ProcessBuilder command = new ProcessBuilder("bash", "scripts/ensure-production-up.sh").redirectErrorStream(true);
        command.environment().put("PATH", bin + ":" + System.getenv("PATH"));
        command.environment().put("DOOIT_APP_IMAGE_TAG", imageTag);
        command.environment().put("DOOIT_APP_COMMIT_SHA", explicitCommit);
        command.environment().put("DOOIT_TEST_EXISTING_COMMIT", existingCommit);
        command.environment().put("DOOIT_TEST_OUTPUT", result.toString());
        Process process = command.start();
        boolean finished = process.waitFor(10, TimeUnit.SECONDS);
        if (!finished) process.destroyForcibly();
        assertThat(finished).isTrue();
        String output = new String(process.getInputStream().readAllBytes());
        assertThat(process.exitValue()).as(output).isZero();
        assertThat(Files.readAllLines(result)).containsExactly(expectedTag, expected);
    }
}
