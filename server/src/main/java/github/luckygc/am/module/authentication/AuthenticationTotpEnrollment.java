package github.luckygc.am.module.authentication;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import github.luckygc.am.common.audit.CreationTimeAuditable;

import lombok.Data;

@Data
@Entity
@Table(name = "am_authentication_totp_enrollment")
public class AuthenticationTotpEnrollment implements CreationTimeAuditable {

    @Id
    @Column(name = "token_key", length = 64)
    private String tokenKey;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "encrypted_secret", nullable = false, length = 1000)
    private String encryptedSecret;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
