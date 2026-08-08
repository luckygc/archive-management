package github.luckygc.am.module.authentication;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import github.luckygc.am.common.audit.CreationTimeAuditable;
import github.luckygc.am.common.audit.UpdateTimeAuditable;

import lombok.Data;

@Data
@Entity
@Table(name = "am_authentication_totp_credential")
public class AuthenticationTotpCredential implements CreationTimeAuditable, UpdateTimeAuditable {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "encrypted_secret", nullable = false, length = 1000)
    private String encryptedSecret;

    @Column(name = "last_accepted_step", nullable = false)
    private long lastAcceptedStep;

    @Version private int version;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
