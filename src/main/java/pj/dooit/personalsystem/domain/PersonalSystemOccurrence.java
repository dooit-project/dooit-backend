package pj.dooit.personalsystem.domain;

import pj.dooit.Constant;
import pj.dooit.task.domain.Task;
import pj.dooit.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "`PERSONAL_SYSTEM_OCCURRENCE`")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PersonalSystemOccurrence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "`ID`")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "`SYSTEM_ID`", nullable = false)
    private PersonalSystem system;

    @Column(name = "`SYSTEM_VERSION`", nullable = false)
    private int systemVersion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "`OWNER_USER_ID`", nullable = false)
    private User owner;

    @Column(name = "`SERVICE_DATE`", nullable = false)
    private LocalDate serviceDate;

    @Column(name = "`TIME_ZONE`", nullable = false, length = 50)
    private String timeZone;

    @Column(name = "`SNAPSHOT_DIRECTION_TEXT`", nullable = false, length = 500)
    private String snapshotDirectionText;

    @Column(name = "`SNAPSHOT_TRIGGER_TEXT`", nullable = false, length = 200)
    private String snapshotTriggerText;

    @Column(name = "`SNAPSHOT_STANDARD_ACTION_TEXT`", nullable = false, length = 500)
    private String snapshotStandardActionText;

    @Column(name = "`SNAPSHOT_MINIMUM_ACTION_TEXT`", nullable = false, length = 500)
    private String snapshotMinimumActionText;

    @Column(name = "`SNAPSHOT_NEXT_ACTION`", length = 300)
    private String snapshotNextAction;

    @Column(name = "`SNAPSHOT_NOTIFY_AT_TIME`")
    private LocalTime snapshotNotifyAtTime;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "`LINKED_TASK_ID`")
    private Task linkedTask;

    @Enumerated(EnumType.STRING)
    @Column(name = "`STATUS`", nullable = false, length = 30)
    private PersonalSystemOccurrenceStatus status = PersonalSystemOccurrenceStatus.PENDING;

    @Version
    @Column(name = "`ENTITY_VERSION`", nullable = false)
    private long entityVersion;

    @Column(name = "`CREATED_AT`", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "`UPDATED_AT`")
    private LocalDateTime updatedAt;

    public PersonalSystemOccurrence(PersonalSystemVersion version, User owner, LocalDate serviceDate) {
        this.system = version.getSystem();
        this.systemVersion = version.getVersion();
        this.owner = owner;
        this.serviceDate = serviceDate;
        this.timeZone = version.getScheduleTimeZone();
        this.snapshotDirectionText = version.getDirectionText();
        this.snapshotTriggerText = version.getTriggerText();
        this.snapshotStandardActionText = version.getStandardActionText();
        this.snapshotMinimumActionText = version.getMinimumActionText();
        this.snapshotNextAction = version.getNextAction();
        this.snapshotNotifyAtTime = version.getNotifyAtTime();
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now(Constant.ZONE);
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now(Constant.ZONE);
    }

    public void markPracticed() {
        this.status = PersonalSystemOccurrenceStatus.PRACTICED;
    }

    public void markRested() {
        this.status = PersonalSystemOccurrenceStatus.RESTED;
    }

    public void markPending() {
        this.status = PersonalSystemOccurrenceStatus.PENDING;
    }

    public void linkTask(Task task) {
        if (task == null) {
            throw new IllegalArgumentException("task는 필수입니다.");
        }
        this.linkedTask = task;
    }
}
