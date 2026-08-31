package pj.dooit.dailyplan.domain;

import pj.dooit.Constant;
import pj.dooit.user.domain.User;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "`DAILY_PLAN`")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DailyPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "`ID`")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "`OWNER_USER_ID`", nullable = false)
    private User owner;

    @Column(name = "`PLAN_DATE`", nullable = false)
    private LocalDate date;

    @Enumerated(EnumType.STRING)
    @Column(name = "`STATUS`", nullable = false, length = 30)
    private DailyPlanStatus status = DailyPlanStatus.DRAFT;

    @ElementCollection
    @CollectionTable(name = "`DAILY_PLAN_FOCUS_TASK`", joinColumns = @JoinColumn(name = "`DAILY_PLAN_ID`"))
    @OrderColumn(name = "`FOCUS_ORDER`")
    @Column(name = "`TASK_ID`", nullable = false)
    private List<Long> focusTaskIds = new ArrayList<>();

    @Column(name = "`CONFIRMED_AT`")
    private LocalDateTime confirmedAt;

    @Column(name = "`CLOSED_AT`")
    private LocalDateTime closedAt;

    @Column(name = "`CREATED_AT`", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "`UPDATED_AT`")
    private LocalDateTime updatedAt;

    public DailyPlan(User owner, LocalDate date) {
        if (owner == null) {
            throw new IllegalArgumentException("owner는 필수입니다.");
        }
        if (date == null) {
            throw new IllegalArgumentException("date는 필수입니다.");
        }
        this.owner = owner;
        this.date = date;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now(Constant.ZONE);
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now(Constant.ZONE);
    }

    public void replace(List<Long> focusTaskIds, DailyPlanStatus status) {
        DailyPlanStatus nextStatus = status == null ? DailyPlanStatus.DRAFT : status;
        this.focusTaskIds.clear();
        this.focusTaskIds.addAll(focusTaskIds == null ? List.of() : focusTaskIds);
        applyStatus(nextStatus);
    }

    private void applyStatus(DailyPlanStatus nextStatus) {
        LocalDateTime now = LocalDateTime.now(Constant.ZONE);
        this.status = nextStatus;
        if (nextStatus == DailyPlanStatus.DRAFT) {
            this.confirmedAt = null;
            this.closedAt = null;
            return;
        }
        if (this.confirmedAt == null) {
            this.confirmedAt = now;
        }
        if (nextStatus == DailyPlanStatus.CONFIRMED) {
            this.closedAt = null;
            return;
        }
        if (this.closedAt == null) {
            this.closedAt = now;
        }
    }
}
