package pj.dooit.goal.domain;

import pj.dooit.Constant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "`GOAL_VERSION`")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GoalVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "`ID`")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "`GOAL_ID`", nullable = false)
    private Goal goal;

    @Column(name = "`VERSION`", nullable = false)
    private int version;

    @Column(name = "`TITLE`", nullable = false, length = 120)
    private String title;

    @Column(name = "`DESIRED_OUTCOME`", length = 500)
    private String desiredOutcome;

    @Column(name = "`MOTIVATION_TEXT`", length = 500)
    private String motivationText;

    @Column(name = "`TARGET_DATE`")
    private LocalDate targetDate;

    @Column(name = "`EFFECTIVE_FROM_DATE`", nullable = false)
    private LocalDate effectiveFromDate;

    @Column(name = "`CREATED_AT`", nullable = false)
    private LocalDateTime createdAt;

    public GoalVersion(Goal goal, LocalDate effectiveFromDate) {
        if (goal == null) {
            throw new IllegalArgumentException("goal은 필수입니다.");
        }
        this.goal = goal;
        this.version = goal.getCurrentVersion();
        this.title = goal.getTitle();
        this.desiredOutcome = goal.getDesiredOutcome();
        this.motivationText = goal.getMotivationText();
        this.targetDate = goal.getTargetDate();
        this.effectiveFromDate = effectiveFromDate;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now(Constant.ZONE);
    }
}
