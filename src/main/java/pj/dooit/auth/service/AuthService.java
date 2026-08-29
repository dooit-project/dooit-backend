package pj.dooit.auth.service;

import pj.dooit.auth.config.RefreshTokenProperties;
import pj.dooit.auth.domain.RefreshTokenSession;
import pj.dooit.auth.dto.GuestMergeResultResponse;
import pj.dooit.auth.dto.LoginRequest;
import pj.dooit.auth.dto.LogoutRequest;
import pj.dooit.auth.dto.RefreshRequest;
import pj.dooit.auth.dto.RegisterRequest;
import pj.dooit.auth.dto.TokenResponse;
import pj.dooit.auth.exception.GuestSessionExpiredException;
import pj.dooit.auth.exception.InvalidCredentialsException;
import pj.dooit.auth.exception.RefreshTokenExpiredException;
import pj.dooit.auth.exception.RefreshTokenInvalidException;
import pj.dooit.auth.exception.RefreshTokenReusedException;
import pj.dooit.Constant;
import pj.dooit.auth.repository.RefreshTokenSessionRepository;
import pj.dooit.dday.repository.DdayGoalRepository;
import pj.dooit.notification.domain.PushDeviceToken;
import pj.dooit.notification.repository.PushDeviceTokenRepository;
import pj.dooit.notification.repository.PushNotificationHistoryRepository;
import pj.dooit.task.repository.RecurrenceSeriesRepository;
import pj.dooit.task.repository.TaskTemplateRepository;
import pj.dooit.task.repository.TaskRepository;
import pj.dooit.task.domain.TaskType;
import pj.dooit.user.domain.AccountType;
import pj.dooit.user.domain.User;
import pj.dooit.user.dto.UserResponse;
import pj.dooit.user.exception.UserEmailAlreadyExistsException;
import pj.dooit.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final TaskRepository taskRepository;
    private final DdayGoalRepository ddayGoalRepository;
    private final RecurrenceSeriesRepository recurrenceSeriesRepository;
    private final TaskTemplateRepository taskTemplateRepository;
    private final PushDeviceTokenRepository pushDeviceTokenRepository;
    private final PushNotificationHistoryRepository pushNotificationHistoryRepository;
    private final RefreshTokenSessionRepository refreshTokenSessionRepository;
    private final RefreshTokenProperties refreshTokenProperties;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest request) {
        return login(null, request);
    }

    @Transactional
    public TokenResponse login(JwtTokenPrincipal guestPrincipal, LoginRequest request) {
        User user = userRepository.findByEmail(normalizeEmail(request.email()))
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        User target = user;
        GuestMergeResultResponse mergeResult = null;
        if (guestPrincipal != null && guestPrincipal.accountType() == AccountType.GUEST) {
            GuestMergeResult result = mergeGuestIntoRegisteredUser(guestPrincipal.userId(), user);
            target = result.target();
            mergeResult = result.mergeResult();
        }

        JwtTokenService.AccessToken accessToken = jwtTokenService.createAccessToken(target);
        RefreshTokenIssue refreshToken = issueRefreshToken(target, null);
        return tokenResponse(target, accessToken, refreshToken, mergeResult);
    }

    @Transactional
    public TokenResponse createGuest() {
        User guest = userRepository.save(User.guest(
                LocalDateTime.now(Constant.ZONE).plus(jwtTokenService.guestAccessTokenTtl())
        ));
        JwtTokenService.AccessToken accessToken = jwtTokenService.createGuestAccessToken(guest);
        return new TokenResponse(
                "Bearer",
                accessToken.tokenValue(),
                accessToken.expiresAt(),
                UserResponse.from(guest)
        );
    }

    @Transactional
    public TokenResponse refreshGuest(AuthService.JwtTokenPrincipal guestPrincipal) {
        if (guestPrincipal == null || guestPrincipal.accountType() != AccountType.GUEST) {
            throw new AuthenticationCredentialsNotFoundException("인증 정보가 필요합니다.");
        }

        User guest = userRepository.findWithLockById(guestPrincipal.userId())
                .orElseThrow(GuestSessionExpiredException::new);
        if (guest.getAccountType() != AccountType.GUEST
                || guest.getMergedIntoUserId() != null
                || isExpiredGuest(guest)) {
            throw new GuestSessionExpiredException();
        }

        guest.refreshGuestExpiration(LocalDateTime.now(Constant.ZONE).plus(jwtTokenService.guestAccessTokenTtl()));
        JwtTokenService.AccessToken accessToken = jwtTokenService.createGuestAccessToken(guest);
        return new TokenResponse(
                "Bearer",
                accessToken.tokenValue(),
                accessToken.expiresAt(),
                UserResponse.from(guest)
        );
    }

    @Transactional
    public TokenResponse promoteGuest(JwtTokenPrincipal guestPrincipal, RegisterRequest request) {
        if (guestPrincipal == null || guestPrincipal.accountType() != AccountType.GUEST) {
            throw new InvalidCredentialsException();
        }

        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new UserEmailAlreadyExistsException(email);
        }

        User guest = userRepository.findWithLockById(guestPrincipal.userId())
                .orElseThrow(GuestSessionExpiredException::new);
        if (guest.getAccountType() != AccountType.GUEST
                || guest.getMergedIntoUserId() != null
                || isExpiredGuest(guest)) {
            throw new GuestSessionExpiredException();
        }

        guest.promoteGuest(
                email,
                passwordEncoder.encode(request.password()),
                request.displayName()
        );

        JwtTokenService.AccessToken accessToken = jwtTokenService.createAccessToken(guest);
        RefreshTokenIssue refreshToken = issueRefreshToken(guest, null);
        return tokenResponse(guest, accessToken, refreshToken, null);
    }

    @Transactional
    public TokenResponse refresh(RefreshRequest request) {
        LocalDateTime now = LocalDateTime.now(Constant.ZONE);
        RefreshTokenSession current = refreshTokenSessionRepository.findByTokenHash(hash(request.refreshToken()))
                .orElseThrow(RefreshTokenInvalidException::new);

        if (current.getRevokedAt() != null || current.getReplacedAt() != null) {
            revokeFamily(current.getFamilyId(), now);
            throw new RefreshTokenReusedException();
        }
        if (current.isExpired(now)) {
            current.revoke(now);
            throw new RefreshTokenExpiredException();
        }
        if (current.getUser().getMergedIntoUserId() != null) {
            current.revoke(now);
            throw new RefreshTokenInvalidException();
        }

        current.markReplaced(now);
        RefreshTokenIssue refreshToken = issueRefreshToken(current.getUser(), current.getFamilyId());
        JwtTokenService.AccessToken accessToken = current.getUser().getAccountType() == AccountType.GUEST
                ? jwtTokenService.createGuestAccessToken(current.getUser())
                : jwtTokenService.createAccessToken(current.getUser());
        return tokenResponse(current.getUser(), accessToken, refreshToken, null);
    }

    @Transactional
    public void logout(LogoutRequest request, User user) {
        LocalDateTime now = LocalDateTime.now(Constant.ZONE);
        if (request != null && request.refreshToken() != null && !request.refreshToken().isBlank()) {
            refreshTokenSessionRepository.findByTokenHash(hash(request.refreshToken()))
                    .filter(session -> session.getUser().getId().equals(user.getId()))
                    .ifPresent(session -> session.revoke(now));
            return;
        }
        refreshTokenSessionRepository.findByUserIdAndRevokedAtIsNull(user.getId())
                .forEach(session -> session.revoke(now));
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private boolean isExpiredGuest(User guest) {
        return guest.getGuestExpiresAt() != null
                && guest.getGuestExpiresAt().isBefore(LocalDateTime.now(Constant.ZONE));
    }

    public record JwtTokenPrincipal(Long userId, AccountType accountType) {
    }

    private GuestMergeResult mergeGuestIntoRegisteredUser(Long guestUserId, User authenticatedTarget) {
        User target = userRepository.findWithLockById(authenticatedTarget.getId())
                .orElseThrow(InvalidCredentialsException::new);
        User guest = userRepository.findWithLockById(guestUserId)
                .orElseThrow(GuestSessionExpiredException::new);

        if (guest.getAccountType() != AccountType.GUEST) {
            if (target.getId().equals(guest.getMergedIntoUserId())) {
                return new GuestMergeResult(target, emptyMergeResult());
            }
            throw new InvalidCredentialsException();
        }
        if (guest.getMergedIntoUserId() != null) {
            if (target.getId().equals(guest.getMergedIntoUserId())) {
                return new GuestMergeResult(target, emptyMergeResult());
            }
            throw new InvalidCredentialsException();
        }
        if (isExpiredGuest(guest)) {
            throw new GuestSessionExpiredException();
        }
        if (target.getId().equals(guest.getId())) {
            throw new InvalidCredentialsException();
        }

        TaskMergeCount taskMergeCount = reassignTasks(guest, target);
        int ddayCount = reassignDdayGoals(guest, target);
        int recurrenceSeriesCount = reassignRecurrenceSeries(guest, target);
        reassignTaskTemplates(guest, target);
        MergePushTokenResult pushTokenResult = reassignPushDeviceTokens(guest, target);
        int pushHistoryCount = reassignPushNotificationHistories(guest, target);
        guest.markMergedInto(target);
        refreshTokenSessionRepository.findByUserIdAndRevokedAtIsNull(guest.getId())
                .forEach(session -> session.revoke(LocalDateTime.now(Constant.ZONE)));

        log.info(
                "Guest account merged: guestUserId={}, targetUserId={}, tasks={}, ddayGoals={}, recurrenceSeries={}, pushTokens={}, skippedPushTokens={}, pushHistories={}",
                guest.getId(),
                target.getId(),
                taskMergeCount.totalCount(),
                ddayCount,
                recurrenceSeriesCount,
                pushTokenResult.reassignedCount(),
                pushTokenResult.skippedDuplicateCount(),
                pushHistoryCount
        );
        return new GuestMergeResult(target, new GuestMergeResultResponse(
                taskMergeCount.taskCount(),
                taskMergeCount.scheduleCount(),
                ddayCount,
                recurrenceSeriesCount
        ));
    }

    private TaskMergeCount reassignTasks(User guest, User target) {
        java.util.List<pj.dooit.task.domain.Task> tasks = taskRepository.findByOwnerId(guest.getId());
        int scheduleCount = 0;
        int taskCount = 0;
        for (pj.dooit.task.domain.Task task : tasks) {
            if (task.getType() == TaskType.SCHEDULE) {
                scheduleCount++;
            } else {
                taskCount++;
            }
        }
        tasks.forEach(task -> task.assignOwner(target));
        return new TaskMergeCount(taskCount, scheduleCount);
    }

    private int reassignDdayGoals(User guest, User target) {
        java.util.List<pj.dooit.dday.domain.DdayGoal> ddayGoals =
                ddayGoalRepository.findAllByOwnerIdOrderByTargetDateAscIdAsc(guest.getId());
        ddayGoals.forEach(ddayGoal -> ddayGoal.assignOwner(target));
        return ddayGoals.size();
    }

    private int reassignRecurrenceSeries(User guest, User target) {
        java.util.List<pj.dooit.task.domain.RecurrenceSeries> recurrenceSeries =
                recurrenceSeriesRepository.findByOwnerId(guest.getId());
        recurrenceSeries.forEach(series -> series.assignOwner(target));
        return recurrenceSeries.size();
    }

    private void reassignTaskTemplates(User guest, User target) {
        taskTemplateRepository.findByOwnerId(guest.getId()).forEach(template -> template.assignOwner(target));
    }

    private MergePushTokenResult reassignPushDeviceTokens(User guest, User target) {
        int reassigned = 0;
        int skippedDuplicate = 0;
        for (PushDeviceToken token : pushDeviceTokenRepository.findByOwnerId(guest.getId())) {
            if (pushDeviceTokenRepository.findByOwnerIdAndDeviceToken(target.getId(), token.getDeviceToken()).isPresent()) {
                token.deactivate();
                skippedDuplicate++;
                continue;
            }
            token.assignOwner(target);
            reassigned++;
        }
        return new MergePushTokenResult(reassigned, skippedDuplicate);
    }

    private int reassignPushNotificationHistories(User guest, User target) {
        java.util.List<pj.dooit.notification.domain.PushNotificationHistory> histories =
                pushNotificationHistoryRepository.findByOwnerId(guest.getId());
        histories.forEach(history -> history.assignOwner(target));
        return histories.size();
    }

    private record MergePushTokenResult(int reassignedCount, int skippedDuplicateCount) {
    }

    private TokenResponse tokenResponse(
            User user,
            JwtTokenService.AccessToken accessToken,
            RefreshTokenIssue refreshToken,
            GuestMergeResultResponse mergeResult
    ) {
        return new TokenResponse(
                "Bearer",
                accessToken.tokenValue(),
                accessToken.expiresAt(),
                refreshToken.tokenValue(),
                refreshToken.refreshExpiresAt(),
                UserResponse.from(user),
                mergeResult
        );
    }

    private RefreshTokenIssue issueRefreshToken(User user, String familyId) {
        LocalDateTime now = LocalDateTime.now(Constant.ZONE);
        String tokenValue = generateRefreshToken();
        LocalDateTime absoluteExpiresAt = now.plus(refreshTokenProperties.absoluteTtl());
        LocalDateTime idleExpiresAt = now.plus(refreshTokenProperties.idleTtl());
        LocalDateTime refreshExpiresAt = idleExpiresAt.isBefore(absoluteExpiresAt) ? idleExpiresAt : absoluteExpiresAt;
        refreshTokenSessionRepository.save(new RefreshTokenSession(
                user,
                familyId == null ? UUID.randomUUID().toString() : familyId,
                hash(tokenValue),
                refreshExpiresAt,
                absoluteExpiresAt
        ));
        return new RefreshTokenIssue(tokenValue, refreshExpiresAt);
    }

    private void revokeFamily(String familyId, LocalDateTime now) {
        refreshTokenSessionRepository.findByFamilyId(familyId)
                .forEach(session -> session.revoke(now));
    }

    private String generateRefreshToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String token) {
        if (token == null || token.isBlank()) {
            throw new RefreshTokenInvalidException();
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm is unavailable", e);
        }
    }

    private record RefreshTokenIssue(String tokenValue, LocalDateTime refreshExpiresAt) {
    }

    private record GuestMergeResult(User target, GuestMergeResultResponse mergeResult) {
    }

    private record TaskMergeCount(int taskCount, int scheduleCount) {

        int totalCount() {
            return taskCount + scheduleCount;
        }
    }

    private GuestMergeResultResponse emptyMergeResult() {
        return new GuestMergeResultResponse(0, 0, 0, 0);
    }
}
