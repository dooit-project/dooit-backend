package pj.dooit.task.dto;

public record TaskCategorySummaryResponse(
        String category,
        String displayName,
        long taskCount,
        long inboxCount,
        long todayCount,
        long doneCount
) {
}
