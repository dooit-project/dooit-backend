package com.todolab.task.repository;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.todolab.common.domain.ResourceScope;
import com.todolab.task.domain.QTask;
import com.todolab.task.domain.Task;
import com.todolab.task.domain.TaskStatus;
import com.todolab.task.domain.TaskType;
import jakarta.persistence.EntityManager;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class TaskRepositoryImpl implements TaskRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    public TaskRepositoryImpl(EntityManager em) {
        this.queryFactory = new JPAQueryFactory(em);
    }

    @Override
    public List<Task> findByDateRange(LocalDateTime start, LocalDateTime end) {
        return findByDateRange(null, start, end);
    }

    @Override
    public List<Task> findByDateRange(Long ownerId, LocalDateTime start, LocalDateTime end) {
        QTask t = QTask.task;

        return queryFactory
                .selectFrom(t)
                .leftJoin(t.ddayGoal).fetchJoin()
                .where(
                        ownerIdEq(t, ownerId),
                        personalScope(t),
                        t.startAt.isNotNull(),
                        overlapsRange(t, start, end)
                )
                .orderBy(t.startAt.asc(), t.id.asc())
                .fetch();
    }

    @Override
    public List<Task> findByDateRangeAndType(LocalDateTime start, LocalDateTime end, TaskType taskType) {
        return findByDateRangeAndType(null, start, end, taskType);
    }

    @Override
    public List<Task> findByDateRangeAndType(Long ownerId, LocalDateTime start, LocalDateTime end, TaskType taskType) {
        QTask t = QTask.task;

        return queryFactory
                .selectFrom(t)
                .leftJoin(t.ddayGoal).fetchJoin()
                .where(
                        ownerIdEq(t, ownerId),
                        personalScope(t),
                        t.type.eq(taskType),
                        t.startAt.isNotNull(),
                        overlapsRange(t, start, end)
                )
                .orderBy(t.startAt.asc(), t.id.asc())
                .fetch();
    }

    @Override
    public List<Task> findWorkspaceByDateRangeAndType(Long workspaceId, LocalDateTime start, LocalDateTime end, TaskType taskType) {
        QTask t = QTask.task;

        return queryFactory
                .selectFrom(t)
                .leftJoin(t.ddayGoal).fetchJoin()
                .where(
                        workspaceIdEq(t, workspaceId),
                        workspaceScope(t),
                        t.type.eq(taskType),
                        t.startAt.isNotNull(),
                        overlapsRange(t, start, end)
                )
                .orderBy(t.startAt.asc(), t.id.asc())
                .fetch();
    }

    @Override
    public List<Task> findUnscheduledTask() {
        return findUnscheduledTask(null);
    }

    @Override
    public List<Task> findUnscheduledTask(Long ownerId) {
        QTask t = QTask.task;

        return queryFactory
                .selectFrom(t)
                .where(
                        ownerIdEq(t, ownerId),
                        personalScope(t),
                        t.startAt.isNull(),
                        t.endAt.isNull()
                )
                .orderBy(t.id.asc())
                .fetch();
    }

    @Override
    public List<Task> findByStatus(TaskStatus status) {
        return findByStatus(null, status);
    }

    @Override
    public List<Task> findByStatus(Long ownerId, TaskStatus status) {
        QTask t = QTask.task;

        return queryFactory
                .selectFrom(t)
                .where(
                        ownerIdEq(t, ownerId),
                        personalScope(t),
                        t.status.eq(status)
                )
                .orderBy(t.createdAt.asc(), t.id.asc())
                .fetch();
    }

    @Override
    public List<Task> findPlannedTasks(LocalDate fromInclusive, LocalDate toExclusive) {
        return findPlannedTasks(null, fromInclusive, toExclusive);
    }

    @Override
    public List<Task> findPlannedTasks(Long ownerId, LocalDate fromInclusive, LocalDate toExclusive) {
        QTask t = QTask.task;

        return queryFactory
                .selectFrom(t)
                .leftJoin(t.ddayGoal).fetchJoin()
                .where(
                        ownerIdEq(t, ownerId),
                        personalScope(t),
                        t.status.eq(TaskStatus.TODAY),
                        plannedDateFrom(t, fromInclusive),
                        plannedDateBefore(t, toExclusive)
                )
                .orderBy(
                        t.targetDate.asc(),
                        timedScheduleFirst(t).asc(),
                        t.startAt.asc(),
                        t.todayOrder.asc().nullsLast(),
                        t.createdAt.asc(),
                        t.id.asc()
                )
                .fetch();
    }

    @Override
    public List<Task> findTodayTasks(LocalDate targetDate) {
        return findTodayTasks(null, targetDate);
    }

    @Override
    public List<Task> findTodayTasks(Long ownerId, LocalDate targetDate) {
        return findTodayTasks(
                ownerId,
                targetDate,
                targetDate.atStartOfDay(),
                targetDate.plusDays(1).atStartOfDay()
        );
    }

    @Override
    public List<Task> findTodayTasks(Long ownerId, LocalDate targetDate, LocalDateTime scheduleStart, LocalDateTime scheduleEnd) {
        QTask t = QTask.task;

        return queryFactory
                .selectFrom(t)
                .leftJoin(t.ddayGoal).fetchJoin()
                .where(
                        ownerIdEq(t, ownerId),
                        personalScope(t),
                        t.status.eq(TaskStatus.TODAY),
                        t.targetDate.eq(targetDate)
                                .or(t.type.eq(TaskType.SCHEDULE)
                                        .and(t.startAt.isNotNull())
                                        .and(overlapsRange(t, scheduleStart, scheduleEnd)))
                )
                .orderBy(
                        executableTaskFirst(t).asc(),
                        t.todayOrder.asc().nullsLast(),
                        t.startAt.asc().nullsLast(),
                        t.createdAt.asc(),
                        t.id.asc()
                )
                .fetch();
    }

    @Override
    public List<Task> findNotificationCandidateTasks(Long ownerId, LocalDateTime start, LocalDateTime end) {
        QTask t = QTask.task;

        return queryFactory
                .selectFrom(t)
                .leftJoin(t.ddayGoal).fetchJoin()
                .where(
                        ownerIdEq(t, ownerId),
                        personalScope(t),
                        t.status.eq(TaskStatus.TODAY),
                        t.startAt.isNotNull(),
                        overlapsRange(t, start, end)
                )
                .orderBy(t.startAt.asc(), t.id.asc())
                .fetch();
    }

    @Override
    public List<Task> findReorderableTodayTasks(Long ownerId, LocalDate targetDate) {
        QTask t = QTask.task;

        return queryFactory
                .selectFrom(t)
                .leftJoin(t.ddayGoal).fetchJoin()
                .where(
                        ownerIdEq(t, ownerId),
                        personalScope(t),
                        t.status.eq(TaskStatus.TODAY),
                        t.targetDate.eq(targetDate),
                        t.type.ne(TaskType.SCHEDULE)
                )
                .orderBy(
                        t.todayOrder.asc().nullsLast(),
                        t.createdAt.asc(),
                        t.id.asc()
                )
                .fetch();
    }

    @Override
    public Integer findMaxTodayOrder(LocalDate targetDate) {
        return findMaxTodayOrder(null, targetDate);
    }

    @Override
    public Integer findMaxTodayOrder(Long ownerId, LocalDate targetDate) {
        QTask t = QTask.task;

        return queryFactory
                .select(t.todayOrder.max())
                .from(t)
                .where(
                        ownerIdEq(t, ownerId),
                        personalScope(t),
                        t.status.eq(TaskStatus.TODAY),
                        t.targetDate.eq(targetDate)
                )
                .fetchOne();
    }

    private NumberExpression<Integer> timedScheduleFirst(QTask task) {
        return new com.querydsl.core.types.dsl.CaseBuilder()
                .when(task.allDay.isFalse().and(task.startAt.isNotNull()))
                .then(0)
                .otherwise(1);
    }

    private NumberExpression<Integer> executableTaskFirst(QTask task) {
        return new com.querydsl.core.types.dsl.CaseBuilder()
                .when(task.type.ne(TaskType.SCHEDULE))
                .then(0)
                .otherwise(1);
    }

    private BooleanExpression plannedDateFrom(QTask task, LocalDate fromInclusive) {
        return fromInclusive == null ? null : task.targetDate.goe(fromInclusive);
    }

    private BooleanExpression plannedDateBefore(QTask task, LocalDate toExclusive) {
        return toExclusive == null ? null : task.targetDate.lt(toExclusive);
    }

    @Override
    public List<Task> findDoneTasks(LocalDate completedDate) {
        return findDoneTasks(null, completedDate);
    }

    @Override
    public List<Task> findDoneTasks(Long ownerId, LocalDate completedDate) {
        QTask t = QTask.task;
        LocalDateTime start = completedDate.atStartOfDay();
        LocalDateTime end = completedDate.plusDays(1).atStartOfDay();

        return queryFactory
                .selectFrom(t)
                .leftJoin(t.ddayGoal).fetchJoin()
                .where(
                        ownerIdEq(t, ownerId),
                        personalScope(t),
                        t.status.eq(TaskStatus.DONE),
                        t.completedAt.goe(start),
                        t.completedAt.lt(end)
                )
                .orderBy(t.completedAt.desc(), t.id.asc())
                .fetch();
    }

    @Override
    public List<Task> findDoneTasksBetween(LocalDate startDate, LocalDate endDate) {
        return findDoneTasksBetween(null, startDate, endDate);
    }

    @Override
    public List<Task> findDoneTasksBetween(Long ownerId, LocalDate startDate, LocalDate endDate) {
        QTask t = QTask.task;
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.plusDays(1).atStartOfDay();

        return queryFactory
                .selectFrom(t)
                .leftJoin(t.ddayGoal).fetchJoin()
                .where(
                        ownerIdEq(t, ownerId),
                        personalScope(t),
                        t.status.eq(TaskStatus.DONE),
                        t.completedAt.goe(start),
                        t.completedAt.lt(end)
                )
                .orderBy(t.completedAt.asc(), t.id.asc())
                .fetch();
    }

    @Override
    public List<Task> findByDdayGoalId(Long ddayGoalId) {
        return findByDdayGoalId(null, ddayGoalId);
    }

    @Override
    public List<Task> findByDdayGoalId(Long ownerId, Long ddayGoalId) {
        QTask t = QTask.task;

        return queryFactory
                .selectFrom(t)
                .where(
                        ownerIdEq(t, ownerId),
                        personalScope(t),
                        t.ddayGoal.id.eq(ddayGoalId)
                )
                .orderBy(t.targetDate.asc().nullsLast(), t.createdAt.asc(), t.id.asc())
                .fetch();
    }

    private BooleanExpression ownerIdEq(QTask task, Long ownerId) {
        return ownerId == null ? null : task.owner.id.eq(ownerId);
    }

    private BooleanExpression personalScope(QTask task) {
        return task.scope.eq(ResourceScope.PERSONAL);
    }

    private BooleanExpression workspaceScope(QTask task) {
        return task.scope.eq(ResourceScope.WORKSPACE);
    }

    private BooleanExpression workspaceIdEq(QTask task, Long workspaceId) {
        return workspaceId == null ? null : task.workspace.id.eq(workspaceId);
    }

    private BooleanExpression overlapsRange(QTask t, LocalDateTime start, LocalDateTime end) {
        return singleScheduleInRange(t, start, end)
                .or(periodScheduleOverlapsRange(t, start, end));
    }

    // 단일 일정은 시작 시각이 조회 범위 [start, end)에 포함되면 조회한다.
    private BooleanExpression singleScheduleInRange(QTask t, LocalDateTime start, LocalDateTime end) {
        return t.endAt.isNull()
                .and(t.startAt.goe(start))
                .and(t.startAt.lt(end));
    }

    // 기간 일정은 일정 구간과 조회 범위가 겹치면 조회한다.
    private BooleanExpression periodScheduleOverlapsRange(QTask t, LocalDateTime start, LocalDateTime end) {
        return t.endAt.isNotNull()
                .and(t.startAt.lt(end))
                .and(t.endAt.gt(start));
    }
}
