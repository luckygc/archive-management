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

import lombok.Data;

@Data
@Entity
@Table(name = "am_access_token_call")
public class AccessTokenCall implements CreationAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "token_id")
    private @Nullable Long tokenId;

    @Column(name = "token_prefix", length = 16)
    private @Nullable String tokenPrefix;

    @Column(name = "caller_type", nullable = false, length = 32)
    private String callerType;

    @Column(name = "service_account_id")
    private @Nullable Long serviceAccountId;

    @Column(name = "represented_user_id")
    private @Nullable Long representedUserId;

    @Column(name = "http_method", nullable = false, length = 16)
    private String httpMethod;

    @Column(name = "request_path", nullable = false, length = 512)
    private String requestPath;

    @Column(name = "status_code", nullable = false)
    private int statusCode;

    @Column(name = "duration_ms", nullable = false)
    private long durationMs;

    @Column(name = "source_ip", length = 64)
    private @Nullable String sourceIp;

    @Column(name = "trace_id", length = 128)
    private @Nullable String traceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_reason", length = 32)
    private @Nullable AccessTokenFailureReason failureReason;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_by")
    private @Nullable Long createdBy;
}
