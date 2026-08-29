package pj.dooit.user.dto;

import pj.dooit.user.domain.User;
import pj.dooit.user.domain.AccountType;
import pj.dooit.user.domain.UserRole;

import java.time.LocalDateTime;

public record UserResponse(
        Long id,
        AccountType accountType,
        String email,
        String displayName,
        UserRole role,
        String timeZone,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public UserResponse(
            Long id,
            String email,
            String displayName,
            UserRole role,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        this(id, AccountType.REGISTERED, email, displayName, role, null, createdAt, updatedAt);
    }

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getAccountType(),
                user.getEmail(),
                user.getDisplayName(),
                user.getRole(),
                user.getTimeZone(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
}
