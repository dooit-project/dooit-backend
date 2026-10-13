package pj.dooit.personalsystem.domain;

import pj.dooit.Constant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Entity
@Table(name = "`PERSONAL_SYSTEM_PRACTICE_RESULT`")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PersonalSystemPracticeResult {

    @Id
    @Column(name = "`OCCURRENCE_ID`")
    private Long occurrenceId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "`OCCURRENCE_ID`")
    private PersonalSystemOccurrence occurrence;

    @Enumerated(EnumType.STRING)
    @Column(name = "`ACTION_VARIANT`", nullable = false, length = 30)
    private ActionVariant actionVariant;

    @Column(name = "`PRACTICED_AT`", nullable = false)
    private LocalDateTime practicedAt;

    @Column(name = "`PRACTICED_TIME_ZONE`", nullable = false, length = 50)
    private String practicedTimeZone;

    @Column(name = "`ACCOMPLISHED_NOTE`", length = 1000)
    private String accomplishedNote;

    @Column(name = "`DIFFICULTIES`", length = 200)
    private String difficulties;

    @Enumerated(EnumType.STRING)
    @Column(name = "`NEXT_ACTION_PATCH_KIND`", nullable = false, length = 30)
    private NextActionPatchKind nextActionPatchKind = NextActionPatchKind.KEEP;

    @Column(name = "`NEXT_ACTION_VALUE`", length = 300)
    private String nextActionValue;

    @Column(name = "`CREATED_AT`", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "`UPDATED_AT`")
    private LocalDateTime updatedAt;

    public PersonalSystemPracticeResult(
            PersonalSystemOccurrence occurrence,
            ActionVariant actionVariant,
            LocalDateTime practicedAt,
            String practicedTimeZone,
            String accomplishedNote,
            List<PracticeDifficulty> difficulties,
            NextActionPatchKind nextActionPatchKind,
            String nextActionValue
    ) {
        this.occurrence = occurrence;
        replace(actionVariant, practicedAt, practicedTimeZone, accomplishedNote, difficulties, nextActionPatchKind, nextActionValue);
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now(Constant.ZONE);
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now(Constant.ZONE);
    }

    public void replace(
            ActionVariant actionVariant,
            LocalDateTime practicedAt,
            String practicedTimeZone,
            String accomplishedNote,
            List<PracticeDifficulty> difficulties,
            NextActionPatchKind nextActionPatchKind,
            String nextActionValue
    ) {
        this.actionVariant = actionVariant == null ? ActionVariant.UNKNOWN : actionVariant;
        this.practicedAt = practicedAt;
        this.practicedTimeZone = practicedTimeZone;
        this.accomplishedNote = accomplishedNote;
        this.difficulties = serializeDifficulties(difficulties);
        this.nextActionPatchKind = nextActionPatchKind == null ? NextActionPatchKind.KEEP : nextActionPatchKind;
        this.nextActionValue = nextActionValue;
    }

    public List<PracticeDifficulty> difficulties() {
        if (difficulties == null || difficulties.isBlank()) {
            return List.of();
        }
        return List.of(difficulties.split(","))
                .stream()
                .filter(value -> !value.isBlank())
                .map(PracticeDifficulty::valueOf)
                .toList();
    }

    private String serializeDifficulties(List<PracticeDifficulty> difficulties) {
        if (difficulties == null || difficulties.isEmpty()) {
            return null;
        }
        return difficulties.stream()
                .distinct()
                .map(Enum::name)
                .collect(Collectors.joining(","));
    }
}
