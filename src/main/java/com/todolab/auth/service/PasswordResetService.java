package com.todolab.auth.service;

import com.todolab.Constant;
import com.todolab.auth.config.PasswordResetProperties;
import com.todolab.auth.domain.PasswordResetToken;
import com.todolab.auth.dto.PasswordResetConfirmRequest;
import com.todolab.auth.dto.PasswordResetRequest;
import com.todolab.auth.dto.PasswordResetRequestResponse;
import com.todolab.auth.dto.PasswordResetVerifyRequest;
import com.todolab.auth.dto.PasswordResetVerifyResponse;
import com.todolab.auth.exception.PasswordResetRateLimitExceededException;
import com.todolab.auth.exception.PasswordResetTokenInvalidException;
import com.todolab.auth.repository.PasswordResetTokenRepository;
import com.todolab.auth.repository.RefreshTokenSessionRepository;
import com.todolab.mail.MailService;
import com.todolab.user.domain.AccountType;
import com.todolab.user.domain.User;
import com.todolab.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordResetProperties passwordResetProperties;
    private final PasswordEncoder passwordEncoder;
    private final MailService mailService;
    private final RefreshTokenSessionRepository refreshTokenSessionRepository;

    @Transactional
    public PasswordResetRequestResponse requestReset(PasswordResetRequest request) {
        String email = normalizeEmail(request.email());
        LocalDateTime now = LocalDateTime.now(Constant.ZONE);
        enforceRateLimit(email, now);

        Optional<User> user = userRepository.findByEmail(email)
                .filter(candidate -> candidate.getAccountType() == AccountType.REGISTERED);
        user.ifPresent(value -> createTokenAndSendMail(value, email, now));

        return new PasswordResetRequestResponse(true, passwordResetProperties.tokenTtl().toSeconds());
    }

    @Transactional(readOnly = true)
    public PasswordResetVerifyResponse verify(PasswordResetVerifyRequest request) {
        PasswordResetToken token = requireUsableToken(request.token());
        return new PasswordResetVerifyResponse(true, maskEmail(token.getEmail()));
    }

    @Transactional
    public void confirm(PasswordResetConfirmRequest request) {
        PasswordResetToken token = requireUsableToken(request.token());
        token.getUser().updatePasswordHash(passwordEncoder.encode(request.newPassword()));
        refreshTokenSessionRepository.findByUserIdAndRevokedAtIsNull(token.getUser().getId())
                .forEach(session -> session.revoke(LocalDateTime.now(Constant.ZONE)));
        token.markUsed(LocalDateTime.now(Constant.ZONE));
    }

    private void enforceRateLimit(String email, LocalDateTime now) {
        LocalDateTime windowStart = now.minus(passwordResetProperties.rateLimitWindow());
        long recentRequests = passwordResetTokenRepository.countByEmailAndCreatedAtGreaterThanEqual(email, windowStart);
        if (recentRequests >= passwordResetProperties.maxRequestsPerWindow()) {
            throw new PasswordResetRateLimitExceededException();
        }
    }

    private void createTokenAndSendMail(User user, String email, LocalDateTime now) {
        String rawToken = generateToken();
        PasswordResetToken token = new PasswordResetToken(
                user,
                email,
                hash(rawToken),
                now.plus(passwordResetProperties.tokenTtl())
        );
        passwordResetTokenRepository.save(token);
        mailService.sendText(
                email,
                "ToDoLab 비밀번호 재설정",
                "아래 링크에서 비밀번호를 재설정하세요.\n\n"
                        + passwordResetProperties.linkTemplate().replace("{token}", rawToken)
                        + "\n\n이 링크는 " + passwordResetProperties.tokenTtl().toMinutes() + "분 동안 유효합니다."
        );
    }

    private PasswordResetToken requireUsableToken(String rawToken) {
        PasswordResetToken token = passwordResetTokenRepository.findByTokenHash(hash(rawToken))
                .orElseThrow(PasswordResetTokenInvalidException::new);
        if (!token.isUsable(LocalDateTime.now(Constant.ZONE))) {
            throw new PasswordResetTokenInvalidException();
        }
        return token;
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new PasswordResetTokenInvalidException();
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm is unavailable", e);
        }
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 1) {
            return "***" + email.substring(Math.max(at, 0));
        }
        return email.charAt(0) + "***" + email.substring(at);
    }
}
