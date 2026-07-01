package github.luckygc.am.module.storage;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.CreationTimestamp;
import org.jspecify.annotations.Nullable;

import github.luckygc.am.common.audit.CreationAuditable;

import lombok.Data;

@Data
@Entity
@Table(name = "am_file_link")
public class FileLink implements CreationAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String code;

    @Column(name = "target_type", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private FileLinkTargetType targetType;

    @Column(name = "target_parent_id")
    private @Nullable Long targetParentId;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Column(name = "allowed_user_id")
    private @Nullable Long allowedUserId;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "revoked_at")
    private @Nullable LocalDateTime revokedAt;

    @Column(name = "created_by")
    private @Nullable Long createdBy;

    @Column(name = "created_at", nullable = false)
    @CreationTimestamp
    private LocalDateTime createdAt = LocalDateTime.now();
}
