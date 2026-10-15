package pj.dooit.goal.dto;

public record GoalJourneySummary(
        long practiceOccurrenceCount, long oneOffCompletionCount, long directLogCount, long practiceDateCount,
        long standardCount, long minimumCount, long unknownCount, long restCount, long noRecordOccurrenceCount
) {}
