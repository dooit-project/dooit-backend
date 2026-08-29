package pj.dooit.common.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorCodeTest {

    @Test
    @DisplayName("모든 API 오류 코드는 중복 없이 유지한다")
    void errorCodesAreUnique() {
        long distinctCount = Arrays.stream(ErrorCode.values())
                .map(ErrorCode::getCode)
                .distinct()
                .count();

        assertThat(distinctCount).isEqualTo(ErrorCode.values().length);
    }

    @Test
    @DisplayName("인증 오류 코드는 401과 403을 구분한다")
    void authErrorCodes() {
        assertThat(ErrorCode.INVALID_CREDENTIALS.getStatus().value()).isEqualTo(401);
        assertThat(ErrorCode.INVALID_CREDENTIALS.getCode()).isEqualTo(11001);
        assertThat(ErrorCode.UNAUTHORIZED.getStatus().value()).isEqualTo(401);
        assertThat(ErrorCode.UNAUTHORIZED.getCode()).isEqualTo(11002);
        assertThat(ErrorCode.FORBIDDEN.getStatus().value()).isEqualTo(403);
        assertThat(ErrorCode.FORBIDDEN.getCode()).isEqualTo(11003);
    }
}
