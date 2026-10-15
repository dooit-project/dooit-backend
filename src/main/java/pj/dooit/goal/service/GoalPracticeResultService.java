package pj.dooit.goal.service;

import pj.dooit.goal.domain.GoalPlanOccurrence;
import pj.dooit.goal.domain.GoalOccurrenceStatus;
import pj.dooit.goal.domain.GoalPlanStatus;
import pj.dooit.goal.domain.GoalPracticeResult;
import pj.dooit.goal.domain.NextActionPatchKind;
import pj.dooit.goal.dto.GoalPlanOccurrenceResponse;
import pj.dooit.goal.dto.GoalPracticeResultRequest;
import pj.dooit.goal.dto.GoalPracticeResultResponse;
import pj.dooit.goal.exception.GoalOccurrenceConflictException;
import pj.dooit.goal.exception.GoalOccurrenceNotFoundException;
import pj.dooit.goal.exception.GoalValidationException;
import pj.dooit.goal.repository.GoalPlanOccurrenceRepository;
import pj.dooit.goal.repository.GoalPracticeResultRepository;
import pj.dooit.user.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.DateTimeException;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
public class GoalPracticeResultService {
    private final GoalPlanOccurrenceRepository occurrenceRepository;
    private final GoalPracticeResultRepository resultRepository;
    private final GoalOccurrenceTaskLinkService taskLinkService;

    @Transactional
    public GoalPracticeResultResponse putResult(Long id, GoalPracticeResultRequest request, User owner) {
        GoalPlanOccurrence occurrence = requireOccurrence(id, request.occurrenceVersion(), owner);
        if (occurrence.getStatus() == GoalOccurrenceStatus.RESTED) {
            throw new GoalOccurrenceConflictException("휴식을 취소한 뒤 실천 결과를 기록해주세요.");
        }
        String zone = normalize(request.practicedTimeZone());
        zone = zone == null ? occurrence.getTimeZone() : zone;
        try {
            ZoneId.of(zone);
        } catch (DateTimeException e) {
            throw new GoalValidationException("유효한 IANA timeZone이 아닙니다.");
        }
        NextActionPatchKind patch = request.nextActionPatchKind() == null ? NextActionPatchKind.KEEP : request.nextActionPatchKind();
        String next = normalize(request.nextAction());
        if ((patch == NextActionPatchKind.REPLACE && next == null) || (patch != NextActionPatchKind.REPLACE && next != null)) {
            throw new GoalValidationException("nextAction은 REPLACE일 때만 필수 값으로 사용할 수 있습니다.");
        }
        GoalPracticeResult result = resultRepository.findById(id).orElse(null);
        if (result == null) {
            result = new GoalPracticeResult(occurrence, request.actionVariant(), request.practicedAt(), zone,
                    normalize(request.accomplishedNote()), request.difficulties(), patch, next);
            resultRepository.save(result);
        } else {
            result.replace(request.actionVariant(), request.practicedAt(), zone, normalize(request.accomplishedNote()),
                    request.difficulties(), patch, next);
        }
        occurrence.markPracticed();
        taskLinkService.completeLinkedTask(occurrence, request.practicedAt(), zone);
        occurrenceRepository.flush();
        return GoalPracticeResultResponse.from(result);
    }

    @Transactional
    public void deleteResult(Long id, Long version, User owner) {
        GoalPlanOccurrence occurrence = requireOccurrence(id, version, owner);
        if (occurrence.getStatus() == GoalOccurrenceStatus.PRACTICED) {
            resultRepository.deleteById(id);
            occurrence.markPending();
            taskLinkService.resetLinkedTask(occurrence, false);
        }
    }

    @Transactional
    public GoalPlanOccurrenceResponse rest(Long id, Long version, User owner) {
        GoalPlanOccurrence occurrence = requireOccurrence(id, version, owner);
        if (occurrence.getStatus() == GoalOccurrenceStatus.PRACTICED) {
            throw new GoalOccurrenceConflictException("실천 결과를 취소한 뒤 휴식을 기록해주세요.");
        }
        if (occurrence.getStatus() != GoalOccurrenceStatus.RESTED) {
            occurrence.markRested();
            taskLinkService.resetLinkedTask(occurrence, true);
        }
        occurrenceRepository.flush();
        return GoalPlanOccurrenceResponse.from(occurrence,
                occurrence.getPlan().getStatus() == GoalPlanStatus.ACTIVE, null);
    }

    @Transactional
    public void cancelRest(Long id, Long version, User owner) {
        GoalPlanOccurrence occurrence = requireOccurrence(id, version, owner);
        if (occurrence.getStatus() == GoalOccurrenceStatus.RESTED) {
            occurrence.markPending();
            taskLinkService.resetLinkedTask(occurrence, false);
        }
    }

    private GoalPlanOccurrence requireOccurrence(Long id, Long version, User owner) {
        GoalPlanOccurrence occurrence = occurrenceRepository.findByIdAndOwnerId(id, owner.getId())
                .orElseThrow(() -> new GoalOccurrenceNotFoundException("occurrenceId=" + id));
        if (version != null && version.longValue() != occurrence.getEntityVersion()) {
            throw new GoalOccurrenceConflictException("회차 version이 변경되었습니다.");
        }
        return occurrence;
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
