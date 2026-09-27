package github.luckygc.am.module.archive.authorization;

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
@Table(name = "am_archive_data_scope_subject_rel")
public class ArchiveDataScopeSubjectRelation implements CreationTimeAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "subject_type", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private ArchiveDataScopeSubjectType subjectType;

    @Column(name = "subject_id", nullable = false)
    private Long subjectId;

    @Column(name = "scope_id", nullable = false)
    private Long scopeId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
