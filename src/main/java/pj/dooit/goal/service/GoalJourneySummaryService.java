package pj.dooit.goal.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import pj.dooit.goal.ai.GoalDraftProperties;
import pj.dooit.goal.ai.GoalJourneySummaryProvider;
import pj.dooit.goal.dto.GoalJourneyItem;
import pj.dooit.goal.dto.GoalJourneyResponse;
import pj.dooit.goal.dto.GoalJourneySummaryRequest;
import pj.dooit.goal.dto.GoalJourneySummaryResponse;
import pj.dooit.goal.exception.GoalAiUnavailableException;
import pj.dooit.goal.exception.GoalJourneyConflictException;
import pj.dooit.goal.exception.GoalValidationException;
import pj.dooit.user.domain.User;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

@Service
@RequiredArgsConstructor
public class GoalJourneySummaryService {
    private static final int EVIDENCE_LIMIT = 100;
    private final GoalJourneyService journeys;
    private final GoalDraftProperties properties;
    private final ObjectProvider<GoalJourneySummaryProvider> providers;

    // The journey read transaction ends before the provider call. No draft/cache/persistence writes.
    public GoalJourneySummaryResponse summarize(Long goalId, GoalJourneySummaryRequest request, User owner) {
        if (request.from() == null || request.to() == null || request.from().isAfter(request.to())
                || ChronoUnit.DAYS.between(request.from(), request.to()) >= 366) {
            throw new GoalValidationException("요약은 1~366일 범위로 요청해주세요.");
        }
        GoalJourneyResponse snapshot = journeys.get(goalId, request.from(), request.to(), request.timeZone(), null, EVIDENCE_LIMIT, owner);
        if (request.expectedJourneyVersion() != null && !request.expectedJourneyVersion().equals(snapshot.journeyVersion())) {
            throw new GoalJourneyConflictException("요약 입력 여정이 변경되었습니다.");
        }
        if (!properties.isEnabled()) throw new GoalAiUnavailableException();
        if (snapshot.items().isEmpty()) return response(snapshot, List.of());
        try {
            var provider = providers.getIfAvailable();
            if (provider == null) throw new GoalAiUnavailableException();
            var evidence = new ArrayList<GoalJourneySummaryProvider.Evidence>();
            for (int i = 0; i < snapshot.items().size(); i++) {
                var item = snapshot.items().get(i);
                evidence.add(new GoalJourneySummaryProvider.Evidence(i, item.sourceType(), item.practicedDate(),
                        item.title(), item.actionText(), item.actionVariant(), item.note(), item.nextAction(), item.difficulties()));
            }
            var selection = provider.select(new GoalJourneySummaryProvider.Context(snapshot.range(), snapshot.summary(),
                    evidence, snapshot.nextCursor() != null));
            if (selection == null || selection.evidenceIndices() == null || selection.evidenceIndices().isEmpty()
                    || selection.evidenceIndices().size() > 5) throw new GoalAiUnavailableException();
            var indices = List.copyOf(selection.evidenceIndices());
            var seen = new HashSet<Integer>();
            var highlights = new ArrayList<GoalJourneyItem>();
            for (Integer index : indices) {
                if (index < 0 || index >= snapshot.items().size() || !seen.add(index)) throw new GoalAiUnavailableException();
                highlights.add(snapshot.items().get(index));
            }
            return response(snapshot, highlights);
        } catch (RuntimeException ignored) {
            // Provider failures can contain private evidence and credentials. Discard message and cause.
            throw new GoalAiUnavailableException();
        }
    }

    private GoalJourneySummaryResponse response(GoalJourneyResponse snapshot, List<GoalJourneyItem> highlights) {
        var s = snapshot.summary();
        String overview = "기록된 실천 %d건 · 실천 날짜 %d일 · 휴식 회차 %d건 · 기록 없는 회차 %d건".formatted(
                s.practiceOccurrenceCount() + s.oneOffCompletionCount() + s.directLogCount(),
                s.practiceDateCount(), s.restCount(), s.noRecordOccurrenceCount());
        return new GoalJourneySummaryResponse(snapshot.goalId(), snapshot.range(), snapshot.journeyVersion(), s,
                overview, highlights, snapshot.items().size(), snapshot.nextCursor() != null);
    }
}
