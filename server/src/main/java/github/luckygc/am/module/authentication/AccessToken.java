package github.luckygc.am.module.authentication;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.jspecify.annotations.Nullable;

import github.luckygc.am.common.audit.CreationAuditable;
import github.luckygc.am.common.audit.UpdateAuditable;

import lombok.Data;

@Data
@Entity
@Table(name = "am_access_token")
public class AccessToken implements CreationAuditable, UpdateAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "token_type", nullable = false, length = 32)
    private AccessTokenType tokenType;

    @Column(name = "service_account_id")
    private @Nullable Long serviceAccountId;

    @Column(name = "user_id")
    private @Nullable Long userId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "token_prefix", nullable = false, length = 16)
    private String tokenPrefix;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(nullable = false, length = 2000)
    private String scopes;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "revoked_at")
    private @Nullable LocalDateTime revokedAt;

    @Column(name = "last_used_at")
    private @Nullable LocalDateTime lastUsedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_by")
    private @Nullable Long createdBy;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "updated_by")
    private @Nullable Long updatedBy;
}
