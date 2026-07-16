package github.luckygc.am.module.authentication;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import github.luckygc.am.common.audit.CreationTimeAuditable;
import github.luckygc.am.common.audit.UpdateTimeAuditable;

import lombok.Data;

@Data
@Entity
@Table(name = "am_login_failure_limit")
public class LoginFailureLimit implements CreationTimeAuditable, UpdateTimeAuditable {

    @Id
    @Column(length = 100)
    private String username;

    @Column(name = "failure_count", nullable = false)
    private int failureCount;

    @Column(name = "lock_level", nullable = false)
    private int lockLevel;

    @Column(name = "first_failed_at", nullable = false)
    private LocalDateTime firstFailedAt;

    @Column(name = "last_failed_at", nullable = false)
    private LocalDateTime lastFailedAt;

    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    @Column(name = "cleanup_after", nullable = false)
    private LocalDateTime cleanupAfter;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
