package com.todolab.user.dto;

import com.todolab.user.domain.User;
import com.todolab.user.domain.AccountType;
import com.todolab.user.domain.UserRole;

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
