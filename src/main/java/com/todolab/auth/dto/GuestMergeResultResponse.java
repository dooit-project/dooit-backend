package com.todolab.auth.dto;

public record GuestMergeResultResponse(
        int tasks,
        int schedules,
        int ddayGoals,
        int recurrenceSeries
) {
}
