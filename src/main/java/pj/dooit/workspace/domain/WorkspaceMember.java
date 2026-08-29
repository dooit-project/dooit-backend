package pj.dooit.workspace.domain;

import pj.dooit.Constant;
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
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "`WORKSPACE_MEMBER`",
        uniqueConstraints = {
                @UniqueConstraint(name = "UK_WORKSPACE_MEMBER_WORKSPACE_USER", columnNames = {"`WORKSPACE_ID`", "`USER_ID`"})
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkspaceMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "`ID`")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "`WORKSPACE_ID`", nullable = false)
    private SharedWorkspace workspace;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "`USER_ID`", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "`ROLE`", nullable = false, length = 30)
    private WorkspaceRole role;

    @Enumerated(EnumType.STRING)
    @Column(name = "`STATUS`", nullable = false, length = 30)
    private WorkspaceMemberStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "`INVITED_BY_USER_ID`")
    private User invitedBy;

    @Column(name = "`CREATED_AT`", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "`UPDATED_AT`")
    private LocalDateTime updatedAt;

    public WorkspaceMember(
            SharedWorkspace workspace,
            User user,
            WorkspaceRole role,
            WorkspaceMemberStatus status,
            User invitedBy
    ) {
        if (workspace == null || user == null) {
            throw new IllegalArgumentException("workspace와 user는 필수입니다.");
        }
        this.workspace = workspace;
        this.user = user;
        this.role = role == null ? WorkspaceRole.VIEWER : role;
        this.status = status == null ? WorkspaceMemberStatus.PENDING : status;
        this.invitedBy = invitedBy;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now(Constant.ZONE);
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now(Constant.ZONE);
    }

    public void changeRole(WorkspaceRole role) {
        if (role == null) {
            throw new IllegalArgumentException("role은 필수입니다.");
        }
        this.role = role;
    }

    public void activate() {
        this.status = WorkspaceMemberStatus.ACTIVE;
    }

    public void markPending() {
        this.status = WorkspaceMemberStatus.PENDING;
    }

    public void remove() {
        this.status = WorkspaceMemberStatus.REMOVED;
    }
}
