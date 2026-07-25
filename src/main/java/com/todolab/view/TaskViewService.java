package com.todolab.view;

import com.todolab.Constant;
import com.todolab.dday.dto.DdayGoalResponse;
import com.todolab.dday.service.DdayGoalService;
import com.todolab.task.domain.query.TaskQueryType;
import com.todolab.task.dto.TaskQueryRequest;
import com.todolab.task.dto.TaskResponse;
import com.todolab.task.service.TaskService;
import com.todolab.view.model.CalendarCell;
import com.todolab.view.model.DaySchedule;
import com.todolab.view.model.DdayGoalUi;
import com.todolab.view.model.MonthPageModel;
import com.todolab.view.model.TaskUi;
import com.todolab.view.model.WeekPageModel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TaskViewService {

    private final TaskService taskService;
    private final DdayGoalService ddayGoalService;

    private final String[] dayLabels = {"일", "월", "화", "수", "목", "금", "토"};

    public WeekPageModel getWeekPage(String move, String date) {
        LocalDate targetDate = (date != null && !date.isBlank())
                ? LocalDate.parse(date)
                : LocalDate.now(Constant.ZONE);

        if ("prev".equals(move)) targetDate = targetDate.minusWeeks(1);
        if ("next".equals(move)) targetDate = targetDate.plusWeeks(1);

        LocalDate weekStart = targetDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY));
        LocalDate weekEnd = weekStart.plusDays(6);

        List<TaskResponse> taskList = taskService.getTasks(
                new TaskQueryRequest(TaskQueryType.WEEK, targetDate.toString())
        );
        List<TaskResponse> todayTaskList = taskService.getPlannedTasksBetween(weekStart, weekEnd);
        List<TaskResponse> doneTaskList = taskService.getDoneTasksBetween(weekStart, weekEnd);
        List<DdayGoalResponse> ddayGoals = ddayGoalService.findByDateRange(weekStart, weekEnd);

        List<DaySchedule> weeklyTasks = new ArrayList<>(7);
        for (int i = 0; i < 7; i++) {
            LocalDate day = weekStart.plusDays(i);

            List<TaskUi> uiTasks = mergeTaskUi(
                    taskList.stream()
                            .filter(t -> occursOn(t, day))
                            .toList(),
                    todayTaskList.stream()
                            .filter(t -> day.equals(t.targetDate()))
                            .toList()
            );

            List<DdayGoalUi> uiDdayGoals = ddayGoals.stream()
                    .filter(goal -> goal.targetDate().equals(day))
                    .map(this::toUi)
                    .toList();

            weeklyTasks.add(new DaySchedule(
                    day,
                    dayLabels[i],
                    uiTasks,
                    uiDdayGoals,
                    hasDoneOn(doneTaskList, day),
                    hasStaleOn(todayTaskList, day)
            ));
        }

        LocalDate selectedDate = targetDate;
        if (selectedDate.isBefore(weekStart) || selectedDate.isAfter(weekEnd)) {
            selectedDate = weekStart;
        }

        LocalDate finalSelectedDate = selectedDate;
        DaySchedule selectedSchedule = weeklyTasks.stream()
                .filter(ds -> ds.date().equals(finalSelectedDate))
                .findFirst()
                .orElse(null);

        int weekTotalCount = weeklyTasks.stream()
                .mapToInt(ds -> ds.tasks() == null ? 0 : ds.tasks().size())
                .sum();

        return new WeekPageModel(
                targetDate,
                weekStart,
                weekEnd,
                weekStart + " ~ " + weekEnd,
                selectedDate,
                weeklyTasks,
                selectedSchedule,
                weekTotalCount
        );
    }

    public MonthPageModel getMonthPage(String move, String date) {
        LocalDate targetDate = parseMonthTargetDate(date);

        if ("prev".equals(move)) targetDate = targetDate.minusMonths(1);
        if ("next".equals(move)) targetDate = targetDate.plusMonths(1);

        YearMonth ym = YearMonth.from(targetDate);
        String ymKey = ym.toString();

        LocalDate monthStart = ym.atDay(1);
        LocalDate monthEnd = ym.atEndOfMonth();
        LocalDate gridStart = monthStart.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY));
        LocalDate gridEnd = monthEnd.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY));

        int totalCells = (int) Duration.between(
                gridStart.atStartOfDay(),
                gridEnd.plusDays(1).atStartOfDay()
        ).toDays();
        int weeks = totalCells / 7;

        List<TaskResponse> taskList = taskService.getTasks(
                new TaskQueryRequest(TaskQueryType.MONTH, ymKey)
        );
        List<TaskResponse> todayTaskList = taskService.getPlannedTasksBetween(gridStart, gridEnd);
        List<TaskResponse> doneTaskList = taskService.getDoneTasksBetween(gridStart, gridEnd);
        List<DdayGoalResponse> ddayGoals = ddayGoalService.findByDateRange(gridStart, gridEnd);

        LocalDate selectedDate = (date != null && date.length() == 10)
                ? LocalDate.parse(date)
                : targetDate;

        List<CalendarCell> monthDays = new ArrayList<>(weeks * 7);
        for (int i = 0; i < weeks * 7; i++) {
            LocalDate day = gridStart.plusDays(i);
            boolean inMonth = !day.isBefore(monthStart) && !day.isAfter(monthEnd);

            List<TaskUi> uiTasks = mergeTaskUi(
                    taskList.stream()
                            .filter(t -> occursOn(t, day))
                            .toList(),
                    todayTaskList.stream()
                            .filter(t -> day.equals(t.targetDate()))
                            .toList()
            );

            List<DdayGoalUi> uiDdayGoals = ddayGoals.stream()
                    .filter(goal -> goal.targetDate().equals(day))
                    .map(this::toUi)
                    .toList();

            monthDays.add(new CalendarCell(
                    day,
                    inMonth,
                    uiTasks,
                    uiDdayGoals,
                    hasDoneOn(doneTaskList, day),
                    hasStaleOn(todayTaskList, day)
            ));
        }

        int monthTotalCount = monthDays.stream()
                .mapToInt(c -> c.tasks() == null ? 0 : c.tasks().size())
                .sum();

        return new MonthPageModel(
                targetDate,
                selectedDate,
                monthStart,
                monthEnd,
                ymKey,
                ymKey,
                monthDays,
                monthTotalCount
        );
    }

    private LocalDate parseMonthTargetDate(String date) {
        if (date == null || date.isBlank()) return LocalDate.now(Constant.ZONE);

        String s = date.trim();
        try {
            if (s.length() == 7) {
                return YearMonth.parse(s).atDay(1);
            }
            if (s.length() == 10) {
                return LocalDate.parse(s);
            }
            return LocalDate.now(Constant.ZONE);
        } catch (Exception e) {
            return LocalDate.now(Constant.ZONE);
        }
    }

    private boolean occursOn(TaskResponse task, LocalDate day) {
        if (task == null) return false;
        if (task.unscheduled()) return false;
        if (task.startAt() == null) return false;

        LocalDateTime dayStart = day.atStartOfDay();
        LocalDateTime dayEnd = day.plusDays(1).atStartOfDay();

        LocalDateTime start = task.startAt();
        LocalDateTime end = task.endAt();

        if (end == null) {
            return !start.isBefore(dayStart) && start.isBefore(dayEnd);
        }
        return start.isBefore(dayEnd) && end.isAfter(dayStart);
    }

    private List<TaskUi> mergeTaskUi(List<TaskResponse> schedules, List<TaskResponse> todayTasks) {
        Map<Long, TaskUi> merged = new LinkedHashMap<>();
        schedules.forEach(task -> merged.put(task.id(), toUi(task)));
        todayTasks.forEach(task -> merged.putIfAbsent(task.id(), toUi(task)));
        return new ArrayList<>(merged.values());
    }

    private boolean hasDoneOn(List<TaskResponse> doneTasks, LocalDate day) {
        return doneTasks.stream()
                .map(TaskResponse::completedAt)
                .filter(completedAt -> completedAt != null)
                .map(LocalDateTime::toLocalDate)
                .anyMatch(day::equals);
    }

    private boolean hasStaleOn(List<TaskResponse> todayTasks, LocalDate day) {
        return todayTasks.stream()
                .filter(task -> day.equals(task.targetDate()))
                .anyMatch(task -> task.staleCarryOver() || task.carryOverCount() >= 3);
    }

    private TaskUi toUi(TaskResponse task) {
        LocalDateTime startAt = task.startAt();
        LocalDate date = (startAt != null) ? startAt.toLocalDate() : task.targetDate();
        LocalTime time = (startAt != null && !task.allDay()) ? startAt.toLocalTime() : null;

        return new TaskUi(
                task.id(),
                task.title(),
                task.description(),
                date,
                time,
                task.allDay(),
                task.startAt(),
                task.endAt(),
                task.category(),
                pickColor(task.id())
        );
    }

    private DdayGoalUi toUi(DdayGoalResponse goal) {
        return new DdayGoalUi(
                goal.id(),
                goal.title(),
                goal.targetDate(),
                goal.daysLeft()
        );
    }

    private String pickColor(Long id) {
        String[] colors = {
                "#BFDBFE", "#C4B5FD", "#FDE68A",
                "#FBCFE8", "#BBF7D0"
        };
        if (id == null) {
            return colors[0];
        }
        int idx = Math.floorMod(id.hashCode(), colors.length);
        return colors[idx];
    }
}
