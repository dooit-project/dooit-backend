package com.todolab.auth.service;

import com.todolab.auth.dto.GuestMergeResultResponse;
import com.todolab.auth.dto.LoginRequest;
import com.todolab.auth.dto.RegisterRequest;
import com.todolab.auth.dto.TokenResponse;
import com.todolab.auth.exception.InvalidCredentialsException;
import com.todolab.Constant;
import com.todolab.dday.repository.DdayGoalRepository;
import com.todolab.notification.domain.PushDeviceToken;
import com.todolab.notification.repository.PushDeviceTokenRepository;
import com.todolab.notification.repository.PushNotificationHistoryRepository;
import com.todolab.task.repository.RecurrenceSeriesRepository;
import com.todolab.task.repository.TaskRepository;
import com.todolab.task.domain.TaskType;
import com.todolab.user.domain.AccountType;
import com.todolab.user.domain.User;
import com.todolab.user.dto.UserResponse;
import com.todolab.user.exception.UserEmailAlreadyExistsException;
import com.todolab.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Locale;

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
    private final PushDeviceTokenRepository pushDeviceTokenRepository;
    private final PushNotificationHistoryRepository pushNotificationHistoryRepository;

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
        return new TokenResponse(
                "Bearer",
                accessToken.tokenValue(),
                accessToken.expiresAt(),
                UserResponse.from(target),
                mergeResult
        );
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
                .orElseThrow(() -> new AuthenticationCredentialsNotFoundException("인증 정보가 올바르지 않습니다."));
        if (guest.getAccountType() != AccountType.GUEST
                || guest.getMergedIntoUserId() != null
                || isExpiredGuest(guest)) {
            throw new AuthenticationCredentialsNotFoundException("인증 정보가 올바르지 않습니다.");
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
                .orElseThrow(InvalidCredentialsException::new);
        if (guest.getAccountType() != AccountType.GUEST) {
            throw new InvalidCredentialsException();
        }

        guest.promoteGuest(
                email,
                passwordEncoder.encode(request.password()),
                request.displayName()
        );

        JwtTokenService.AccessToken accessToken = jwtTokenService.createAccessToken(guest);
        return new TokenResponse(
                "Bearer",
                accessToken.tokenValue(),
                accessToken.expiresAt(),
                UserResponse.from(guest)
        );
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
                .orElseThrow(InvalidCredentialsException::new);

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
        if (target.getId().equals(guest.getId())) {
            throw new InvalidCredentialsException();
        }

        TaskMergeCount taskMergeCount = reassignTasks(guest, target);
        int ddayCount = reassignDdayGoals(guest, target);
        int recurrenceSeriesCount = reassignRecurrenceSeries(guest, target);
        MergePushTokenResult pushTokenResult = reassignPushDeviceTokens(guest, target);
        int pushHistoryCount = reassignPushNotificationHistories(guest, target);
        guest.markMergedInto(target);

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
        java.util.List<com.todolab.task.domain.Task> tasks = taskRepository.findByOwnerId(guest.getId());
        int scheduleCount = 0;
        int taskCount = 0;
        for (com.todolab.task.domain.Task task : tasks) {
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
        java.util.List<com.todolab.dday.domain.DdayGoal> ddayGoals =
                ddayGoalRepository.findAllByOwnerIdOrderByTargetDateAscIdAsc(guest.getId());
        ddayGoals.forEach(ddayGoal -> ddayGoal.assignOwner(target));
        return ddayGoals.size();
    }

    private int reassignRecurrenceSeries(User guest, User target) {
        java.util.List<com.todolab.task.domain.RecurrenceSeries> recurrenceSeries =
                recurrenceSeriesRepository.findByOwnerId(guest.getId());
        recurrenceSeries.forEach(series -> series.assignOwner(target));
        return recurrenceSeries.size();
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
        java.util.List<com.todolab.notification.domain.PushNotificationHistory> histories =
                pushNotificationHistoryRepository.findByOwnerId(guest.getId());
        histories.forEach(history -> history.assignOwner(target));
        return histories.size();
    }

    private record MergePushTokenResult(int reassignedCount, int skippedDuplicateCount) {
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
