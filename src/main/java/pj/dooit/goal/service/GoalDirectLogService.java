package pj.dooit.goal.service;

import pj.dooit.Constant;
import pj.dooit.common.idempotency.IdempotencyKeyReusedException;
import pj.dooit.goal.domain.Goal;
import pj.dooit.goal.domain.GoalDirectLog;
import pj.dooit.goal.domain.GoalVersion;
import pj.dooit.goal.dto.GoalDirectLogRequest;
import pj.dooit.goal.dto.GoalDirectLogResponse;
import pj.dooit.goal.exception.GoalDirectLogConflictException;
import pj.dooit.goal.exception.GoalDirectLogNotFoundException;
import pj.dooit.goal.exception.GoalNotFoundException;
import pj.dooit.goal.exception.GoalValidationException;
import pj.dooit.goal.repository.GoalDirectLogRepository;
import pj.dooit.goal.repository.GoalRepository;
import pj.dooit.goal.repository.GoalVersionRepository;
import pj.dooit.user.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class GoalDirectLogService {
    private final GoalDirectLogRepository logRepository;
    private final GoalRepository goalRepository;
    private final GoalVersionRepository versionRepository;

    @Transactional
    public CreateResult create(Long goalId, String key, GoalDirectLogRequest request, User owner) {
        key = normalize(key);
        if (key == null || key.length() > 160) {
            throw new GoalValidationException("Idempotency-Key는 1~160자 필수 값입니다.");
        }
        GoalDirectLogRequest normalized = normalizeRequest(request);
        String hash = requestHash(normalized);
        // A database row lock and the unique key protect creation across concurrent app instances.
        Goal goal = goalRepository.findForUpdate(goalId, owner.getId())
                .orElseThrow(() -> new GoalNotFoundException("goalId=" + goalId));
        GoalDirectLog existing = logRepository.findByOwnerIdAndGoalIdAndIdempotencyKey(owner.getId(), goalId, key).orElse(null);
        if (existing != null) {
            if (!hash.equals(existing.getCreateRequestHash())) {
                throw new IdempotencyKeyReusedException();
            }
            return new CreateResult(GoalDirectLogResponse.from(existing), true);
        }
        GoalVersion snapshot = null;
        for (GoalVersion version : versionRepository.findByGoalIdAndEffectiveFromDateLessThanEqualOrderByVersionAsc(
                goalId, normalized.practicedDate())) {
            snapshot = version;
        }
        if (snapshot == null) {
            snapshot = versionRepository.findByGoalIdAndVersion(goalId, 1).orElseThrow();
        }
        GoalDirectLog log = logRepository.saveAndFlush(new GoalDirectLog(owner, goal, snapshot.getVersion(),
                normalized.practicedDate(), normalized.practicedTimeZone(), normalized.title(), normalized.note(), key, hash));
        return new CreateResult(GoalDirectLogResponse.from(log), false);
    }

    @Transactional
    public GoalDirectLogResponse update(Long id, GoalDirectLogRequest request, User owner) {
        GoalDirectLog log = requireLog(id, owner);
        if (log.getCanceledAt() != null) {
            throw new GoalDirectLogConflictException("취소한 직접 기록은 수정할 수 없습니다.");
        }
        GoalDirectLogRequest normalized = normalizeRequest(request);
        log.replace(normalized.practicedDate(), normalized.practicedTimeZone(), normalized.title(), normalized.note());
        logRepository.flush();
        return GoalDirectLogResponse.from(log);
    }

    @Transactional
    public void cancel(Long id, User owner) {
        requireLog(id, owner).cancel();
    }

    private GoalDirectLog requireLog(Long id, User owner) {
        return logRepository.findByIdAndOwnerId(id, owner.getId())
                .orElseThrow(() -> new GoalDirectLogNotFoundException("logId=" + id));
    }

    private GoalDirectLogRequest normalizeRequest(GoalDirectLogRequest request) {
        String timeZone = normalize(request.practicedTimeZone());
        timeZone = timeZone == null ? Constant.ZONE_ID : timeZone;
        try {
            ZoneId.of(timeZone);
        } catch (DateTimeException e) {
            throw new GoalValidationException("유효한 IANA timeZone이 아닙니다.");
        }
        if (request.practicedDate() == null || normalize(request.title()) == null) {
            throw new GoalValidationException("실천 날짜와 제목은 필수입니다.");
        }
        return new GoalDirectLogRequest(request.practicedDate(), timeZone, normalize(request.title()), normalize(request.note()));
    }

    private String requestHash(GoalDirectLogRequest request) {
        String payload = Stream.of(request.practicedDate().toString(), request.practicedTimeZone(), request.title(), request.note())
                .map(value -> value == null ? "-1:" : value.length() + ":" + value).collect(Collectors.joining());
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record CreateResult(GoalDirectLogResponse response, boolean replayed) {}
}
