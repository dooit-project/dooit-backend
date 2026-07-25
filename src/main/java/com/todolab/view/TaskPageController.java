package com.todolab.view;

import com.todolab.Constant;
import com.todolab.task.dto.TaskResponse;
import com.todolab.task.service.TaskService;
import com.todolab.view.model.MonthPageModel;
import com.todolab.view.model.WeekPageModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.io.StringWriter;
import java.time.LocalDate;
import java.util.Set;

@Slf4j
@Controller
@RequiredArgsConstructor
public class TaskPageController {

    private final SpringTemplateEngine templateEngine;
    private final TaskService taskService;
    private final TaskViewService taskViewService;

    // ===========================
    //  일정 등록 모달
    // ===========================
    @GetMapping(
            value = "/tasks/create",
            headers = "X-Requested-With=fetch",
            produces = MediaType.TEXT_HTML_VALUE
    )
    @ResponseBody
    public String createFragment() {
        Context ctx = new Context();

        StringWriter writer = new StringWriter();
        templateEngine.process(
                "pages/task/create",
                Set.of("#create-page"),
                ctx,
                writer
        );

        return writer.toString();
    }

    // ===========================
    //  일정 상세 모달
    // ===========================
    @GetMapping(
            value = "/tasks/detail",
            headers = "X-Requested-With=fetch",
            produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    public String detailFragment(@RequestParam Long id) {

        TaskResponse task = taskService.getTask(id);

        Context ctx = new Context();
        ctx.setVariable("task", task);

        StringWriter writer = new StringWriter();
        templateEngine.process(
                "pages/task/detail",
                Set.of("#detail-fragment"),
                ctx,
                writer
        );

        return writer.toString();
    }

    // ===========================
    //  기록함 레거시 진입점
    // ===========================
    @GetMapping({"/tasks/unscheduled", "/tasks/inbox"})
    public String unscheduled() {
        return "redirect:/tasks/today";
    }

    // ===========================
    //  일간 일정 페이지
    // ===========================
    @GetMapping("/tasks/today")
    public String today(Model model) {

        LocalDate today = LocalDate.now(Constant.ZONE);

        model.addAttribute("title", "ToDoLab");
        model.addAttribute("showBaseHeader", false);
        model.addAttribute("headerTitle", "Today");
        model.addAttribute("activeTab", "today");
        model.addAttribute("date", today);
        model.addAttribute("contentView", "pages/task/today");
        return "layout/base";
    }

    // ===========================
    //  완료 로그 페이지
    // ===========================
    @GetMapping({"/tasks/log", "/tasks/done"})
    public String doneLog(
            @RequestParam(name = "date", required = false) String date,
            Model model
    ) {
        LocalDate selectedDate = date == null || date.isBlank()
                ? LocalDate.now(Constant.ZONE)
                : LocalDate.parse(date);

        model.addAttribute("title", "ToDoLab");
        model.addAttribute("showBaseHeader", false);
        model.addAttribute("headerTitle", "Log");
        model.addAttribute("activeTab", "log");
        model.addAttribute("date", selectedDate);
        model.addAttribute("contentView", "pages/task/log");
        return "layout/base";
    }

    // ===========================
    //  D-Day 페이지
    // ===========================
    @GetMapping("/tasks/dday")
    public String dday(Model model) {
        model.addAttribute("title", "ToDoLab");
        model.addAttribute("showBaseHeader", false);
        model.addAttribute("headerTitle", "D-Day");
        model.addAttribute("activeTab", "dday");
        model.addAttribute("contentView", "pages/task/dday");
        return "layout/base";
    }

    // ===========================
    //  더보기 페이지
    // ===========================
    @GetMapping("/tasks/more")
    public String more(Model model) {
        model.addAttribute("title", "ToDoLab");
        model.addAttribute("showBaseHeader", false);
        model.addAttribute("headerTitle", "More");
        model.addAttribute("activeTab", "more");
        model.addAttribute("contentView", "pages/task/more");
        return "layout/base";
    }

    // ===========================
    //  캘린더 기본 진입점
    // ===========================
    @GetMapping("/tasks/calendar")
    public String calendar() {
        return "redirect:/tasks/week";
    }

    // ===========================
    //  주간 일정 페이지
    // ===========================
    @GetMapping("/tasks/week")
    public String week(
            @RequestParam(name = "move", required = false) String move,
            @RequestParam(name = "date", required = false) String date,
            Model model
    ) {
        WeekPageModel page = taskViewService.getWeekPage(move, date);

        model.addAttribute("title", "ToDoLab");
        model.addAttribute("showBaseHeader", false);
        model.addAttribute("headerTitle", page.currentDate().getYear() + "년 " + page.currentDate().getMonthValue() + "월");
        model.addAttribute("activeTab", "calendar");

        model.addAttribute("currentDate", page.currentDate());
        model.addAttribute("weekStart", page.weekStart());
        model.addAttribute("weekEnd", page.weekEnd());
        model.addAttribute("weekRange", page.weekRange());

        model.addAttribute("selectedDate", page.selectedDate());
        model.addAttribute("weeklyTasks", page.weeklyTasks());
        model.addAttribute("selectedSchedule", page.selectedSchedule());
        model.addAttribute("weekTotalCount", page.weekTotalCount());

        model.addAttribute("contentView", "pages/task/week");
        return "layout/base";
    }

    // ===========================
    //  월간 일정 페이지 ✅ (MONTH API: date=yyyy-MM)
    // ===========================
    @GetMapping("/tasks/month")
    public String month(
            @RequestParam(name = "move", required = false) String move,
            @RequestParam(name = "date", required = false) String date,
            Model model
    ) {
        MonthPageModel page = taskViewService.getMonthPage(move, date);

        model.addAttribute("title", "ToDoLab");
        model.addAttribute("showBaseHeader", false);
        model.addAttribute("headerTitle", page.currentDate().getYear() + "년 " + page.currentDate().getMonthValue() + "월");
        model.addAttribute("activeTab", "calendar");

        model.addAttribute("currentDate", page.currentDate());
        model.addAttribute("selectedDate", page.selectedDate());

        model.addAttribute("monthStart", page.monthStart());
        model.addAttribute("monthEnd", page.monthEnd());

        model.addAttribute("monthLabel", page.monthLabel());
        model.addAttribute("monthRange", page.monthRange());

        model.addAttribute("monthDays", page.monthDays());
        model.addAttribute("monthTotalCount", page.monthTotalCount());

        model.addAttribute("contentView", "pages/task/month");
        return "layout/base";
    }
}
