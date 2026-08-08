package com.todolab.auth.dto;

import com.todolab.user.domain.AccountType;

public record AuthenticatedUserResponse(
        Long id,
        AccountType accountType,
        String email,
        String displayName,
        String role
) {
}
