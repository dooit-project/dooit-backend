package pj.dooit.auth.dto;

import pj.dooit.user.domain.AccountType;

public record AuthenticatedUserResponse(
        Long id,
        AccountType accountType,
        String email,
        String displayName,
        String role
) {
}
