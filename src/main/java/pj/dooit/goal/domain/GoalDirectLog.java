package pj.dooit.goal.domain;

import pj.dooit.Constant;
import pj.dooit.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "`GOAL_DIRECT_LOG`", uniqueConstraints = @UniqueConstraint(
        name = "UK_GOAL_DIRECT_LOG_IDEMPOTENCY", columnNames = {"OWNER_USER_ID", "GOAL_ID", "IDEMPOTENCY_KEY"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GoalDirectLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "`ID`")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "`OWNER_USER_ID`", nullable = false)
    private User owner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "`GOAL_ID`", nullable = false)
    private Goal goal;

    @Column(name = "`GOAL_VERSION`", nullable = false)
    private int goalVersion;

    @Column(name = "`PRACTICED_DATE`", nullable = false)
    private LocalDate practicedDate;

    @Column(name = "`PRACTICED_TIME_ZONE`", nullable = false, length = 50)
    private String practicedTimeZone;

    @Column(name = "`TITLE`", nullable = false, length = 120)
    private String title;

    @Column(name = "`NOTE`", length = 1000)
    private String note;

    @Column(name = "`IDEMPOTENCY_KEY`", nullable = false, length = 160)
    private String idempotencyKey;

    @Column(name = "`CREATE_REQUEST_HASH`", length = 64)
    private String createRequestHash;

    @Column(name = "`CREATED_AT`", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "`UPDATED_AT`")
    private LocalDateTime updatedAt;

    @Column(name = "`CANCELED_AT`")
    private LocalDateTime canceledAt;

    public GoalDirectLog(User owner, Goal goal, int goalVersion, LocalDate practicedDate, String practicedTimeZone,
                         String title, String note, String idempotencyKey, String createRequestHash) {
        this.owner = owner;
        this.goal = goal;
        this.goalVersion = goalVersion;
        this.idempotencyKey = idempotencyKey;
        this.createRequestHash = createRequestHash;
        replace(practicedDate, practicedTimeZone, title, note);
    }

    public void replace(LocalDate date, String timeZone, String title, String note) {
        this.practicedDate = date;
        this.practicedTimeZone = timeZone;
        this.title = title;
        this.note = note;
    }

    public void cancel() {
        if (this.canceledAt == null) {
            this.canceledAt = LocalDateTime.now(Constant.ZONE);
        }
    }

    @PrePersist
    protected void onCreate() { this.createdAt = LocalDateTime.now(Constant.ZONE); }

    @PreUpdate
    protected void onUpdate() { this.updatedAt = LocalDateTime.now(Constant.ZONE); }
}
