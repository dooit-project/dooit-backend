package pj.dooit.task.domain;

import pj.dooit.Constant;
import pj.dooit.task.exception.TaskValidationException;
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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;

@Entity
@Table(name = "`TASK_CHECKLIST_ITEM`")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TaskChecklistItem {

    public static final int TITLE_MAX_LENGTH = 30;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "`ID`")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "`TASK_ID`", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Task task;

    @Column(name = "`TITLE`", nullable = false, length = TITLE_MAX_LENGTH)
    private String title;

    @Column(name = "`DONE`", nullable = false)
    private boolean done;

    @Column(name = "`SORT_ORDER`", nullable = false)
    private int sortOrder;

    @Column(name = "`COMPLETED_AT`")
    private LocalDateTime completedAt;

    @Column(name = "`CREATED_AT`", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "`UPDATED_AT`")
    private LocalDateTime updatedAt;

    public TaskChecklistItem(Task task, String title, int sortOrder) {
        if (task == null) {
            throw new IllegalArgumentException("task는 필수입니다.");
        }
        if (sortOrder < 0) {
            throw new IllegalArgumentException("sortOrder는 0 이상이어야 합니다.");
        }
        this.task = task;
        updateTitle(title);
        this.sortOrder = sortOrder;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now(Constant.ZONE);
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now(Constant.ZONE);
    }

    public void updateTitle(String title) {
        String normalizedTitle = normalizeTitle(title);
        if (normalizedTitle == null) {
            throw new TaskValidationException("checklist item 제목은 필수입니다.");
        }
        if (normalizedTitle.length() > TITLE_MAX_LENGTH) {
            throw new TaskValidationException("checklist item 제목은 30자 이하여야 합니다.");
        }
        this.title = normalizedTitle;
    }

    public void complete(LocalDateTime completedAt) {
        if (completedAt == null) {
            throw new IllegalArgumentException("completedAt은 필수입니다.");
        }
        this.done = true;
        this.completedAt = completedAt;
    }

    public void reopen() {
        this.done = false;
        this.completedAt = null;
    }

    public void assignSortOrder(int sortOrder) {
        if (sortOrder < 0) {
            throw new IllegalArgumentException("sortOrder는 0 이상이어야 합니다.");
        }
        this.sortOrder = sortOrder;
    }

    private String normalizeTitle(String title) {
        if (title == null) {
            return null;
        }
        String normalized = title.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
