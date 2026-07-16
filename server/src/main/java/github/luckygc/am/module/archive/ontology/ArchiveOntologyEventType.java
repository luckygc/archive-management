package github.luckygc.am.module.archive.ontology;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.hibernate.annotations.SoftDelete;
import org.jspecify.annotations.Nullable;

import github.luckygc.am.common.audit.CreationAuditable;
import github.luckygc.am.common.audit.UpdateAuditable;

import lombok.Data;

@Data
@Entity
@Table(name = "am_archive_ontology_event_type")
@SoftDelete(columnName = "deleted_flag")
public class ArchiveOntologyEventType implements CreationAuditable, UpdateAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_code", nullable = false, length = 100)
    private String eventCode;

    @Column(name = "event_name", nullable = false)
    private String eventName;

    @Column(name = "object_type_id", nullable = false)
    private Long objectTypeId;

    @Column(length = 1000)
    private @Nullable String description;

    @Column(nullable = false)
    private boolean enabled = true;

    @Version
    @Column(nullable = false)
    private int version;

    @Column(name = "created_by")
    private @Nullable Long createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_by")
    private @Nullable Long updatedBy;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
