package github.luckygc.am.module.intake;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import github.luckygc.am.common.audit.CreationTimeAuditable;

import lombok.Data;

@Data
@Entity
@Table(name = "am_archive_intake_package_validation")
public class ArchiveIntakePackageValidation implements CreationTimeAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "intake_package_id", nullable = false)
    private Long intakePackageId;

    @Column(name = "validation_code", nullable = false, length = 30)
    private String validationCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "validation_category", nullable = false, length = 20)
    private ArchiveIntakeValidationCategory validationCategory;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ArchiveIntakeValidationOutcome outcome;

    @Column(nullable = false, length = 500)
    private String message;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
