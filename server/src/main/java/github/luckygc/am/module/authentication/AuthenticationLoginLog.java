package github.luckygc.am.module.authentication;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.CreationTimestamp;

import lombok.Data;

@Data
@Entity
@Table(name = "am_authentication_event")
public class AuthenticationLoginLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_type", nullable = false, length = 30)
    private String eventType;

    @Column(name = "user_id")
    private Long userId;

    @Column(length = 100)
    private String username;

    @Column(name = "display_name", length = 100)
    private String displayName;

    @Column(name = "session_id", length = 100)
    private String sessionId;

    @Column(name = "operator_user_id")
    private Long operatorUserId;

    @Column(name = "operator_username", length = 100)
    private String operatorUsername;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "remote_address", length = 100)
    private String remoteAddress;

    @Column(length = 255)
    private String host;

    @Column(length = 1000)
    private String forwarded;

    @Column(name = "x_forwarded_for", length = 1000)
    private String xForwardedFor;

    @Column(name = "x_real_ip", length = 100)
    private String xRealIp;

    @Column(name = "user_agent", length = 2000)
    private String userAgent;

    @Column(name = "browser_name", length = 100)
    private String browserName;

    @Column(name = "browser_version", length = 100)
    private String browserVersion;

    @Column(name = "os_name", length = 100)
    private String osName;

    @Column(name = "os_version", length = 100)
    private String osVersion;

    @Column(name = "device_type", length = 50)
    private String deviceType;

    @Column(name = "occurred_at", nullable = false)
    @CreationTimestamp
    private LocalDateTime occurredAt;
}
