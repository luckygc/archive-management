package github.luckygc.am.module.authentication;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.Immutable;

import lombok.Data;

@Data
@Entity
@Immutable
@Table(name = "spring_session")
public class SpringSessionRecord {

    @Id
    @Column(name = "primary_id", length = 36)
    private String primaryId;

    @Column(name = "session_id", nullable = false, length = 36)
    private String sessionId;

    @Column(name = "creation_time", nullable = false)
    private long creationTime;

    @Column(name = "last_access_time", nullable = false)
    private long lastAccessTime;

    @Column(name = "max_inactive_interval", nullable = false)
    private int maxInactiveInterval;

    @Column(name = "expiry_time", nullable = false)
    private long expiryTime;

    @Column(name = "principal_name", length = 100)
    private String principalName;
}
