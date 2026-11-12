package github.luckygc.am.module.authorization;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import github.luckygc.am.common.audit.CreationTimeAuditable;

import lombok.Data;

@Data
@Entity
@Table(name = "am_authorization_service_account_role")
public class AuthorizationServiceAccountRoleRelation implements CreationTimeAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "service_account_id", nullable = false)
    private Long serviceAccountId;

    @Column(name = "role_id", nullable = false)
    private Long roleId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
