package pj.dooit.auth.dto;

import pj.dooit.user.dto.UserResponse;

import java.time.LocalDateTime;

public record TokenResponse(
        String tokenType,
        String accessToken,
        LocalDateTime expiresAt,
        String refreshToken,
        LocalDateTime refreshExpiresAt,
        UserResponse user,
        GuestMergeResultResponse mergeResult
) {

    public TokenResponse(String tokenType, String accessToken, LocalDateTime expiresAt, UserResponse user) {
        this(tokenType, accessToken, expiresAt, null, null, user, null);
    }

    public TokenResponse(
            String tokenType,
            String accessToken,
            LocalDateTime expiresAt,
            UserResponse user,
            GuestMergeResultResponse mergeResult
    ) {
        this(tokenType, accessToken, expiresAt, null, null, user, mergeResult);
    }
}
