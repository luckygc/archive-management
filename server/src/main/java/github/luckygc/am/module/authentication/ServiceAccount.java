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

import org.hibernate.annotations.SoftDelete;
import org.jspecify.annotations.Nullable;

import github.luckygc.am.common.audit.CreationAuditable;
import github.luckygc.am.common.audit.UpdateAuditable;

import lombok.Data;

@Data
@Entity
@Table(name = "am_service_account")
@SoftDelete(columnName = "deleted_flag")
public class ServiceAccount implements CreationAuditable, UpdateAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 500)
    private @Nullable String description;

    @Column(name = "owner_user_id", nullable = false)
    private Long ownerUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ServiceAccountStatus status;

    @Column(name = "deleted_at")
    private @Nullable LocalDateTime deletedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_by")
    private @Nullable Long createdBy;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "updated_by")
    private @Nullable Long updatedBy;
}
