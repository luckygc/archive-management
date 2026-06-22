package github.luckygc.am.module.auth;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.CreationTimestamp;

import lombok.Data;

@Data
@Entity
@Table(name = "am_auth_cap_challenge")
public class AuthCapChallenge {

    @Id
    @Column(length = 50)
    private String token;

    @Column(name = "challenge_count", nullable = false)
    private int challengeCount;

    @Column(name = "challenge_size", nullable = false)
    private int challengeSize;

    @Column(nullable = false)
    private int difficulty;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "created_at", nullable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;
}
