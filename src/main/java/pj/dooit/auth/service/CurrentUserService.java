package pj.dooit.auth.service;

import pj.dooit.Constant;
import pj.dooit.auth.exception.GuestSessionExpiredException;
import pj.dooit.user.domain.AccountType;
import pj.dooit.user.domain.User;
import pj.dooit.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CurrentUserService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public User requireUser(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null || jwt.getSubject().isBlank()) {
            throw new AuthenticationCredentialsNotFoundException("인증 정보가 필요합니다.");
        }

        Long userId = parseUserId(jwt.getSubject());
        User user = userRepository.findById(userId)
                .orElseThrow(() -> {
                    if (AccountType.GUEST.name().equals(jwt.getClaimAsString("accountType"))) {
                        return new GuestSessionExpiredException();
                    }
                    return new UsernameNotFoundException("사용자를 찾을 수 없습니다.");
                });
        if (jwt.getClaimAsString("accountType") != null
                && !jwt.getClaimAsString("accountType").equals(user.getAccountType().name())) {
            throw new AuthenticationCredentialsNotFoundException("인증 정보가 올바르지 않습니다.");
        }
        if (user.getMergedIntoUserId() != null) {
            throw new GuestSessionExpiredException();
        }
        if (user.getAccountType() == AccountType.GUEST
                && user.getGuestExpiresAt() != null
                && user.getGuestExpiresAt().isBefore(java.time.LocalDateTime.now(Constant.ZONE))) {
            throw new GuestSessionExpiredException();
        }
        return user;
    }

    private Long parseUserId(String subject) {
        try {
            return Long.valueOf(subject);
        } catch (NumberFormatException e) {
            throw new AuthenticationCredentialsNotFoundException("인증 정보가 올바르지 않습니다.", e);
        }
    }
}
