package github.luckygc.am.module.archive.physical;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.jspecify.annotations.Nullable;

import github.luckygc.am.common.audit.CreationAuditable;

import lombok.Data;

@Data
@Entity
@Table(name = "am_archive_physical_location_history")
public class ArchivePhysicalLocationHistory implements CreationAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "physical_object_id", nullable = false)
    private Long physicalObjectId;

    @Column(name = "from_location_id")
    private @Nullable Long fromLocationId;

    @Column(name = "to_location_id", nullable = false)
    private Long toLocationId;

    @Column(name = "business_type", length = 80)
    private @Nullable String businessType;

    @Column(name = "business_id")
    private @Nullable Long businessId;

    @Column(length = 500)
    private @Nullable String reason;

    @Column(name = "operated_by")
    private @Nullable Long operatedBy;

    @Column(name = "operated_at", nullable = false)
    private LocalDateTime operatedAt;

    @Column(name = "created_by")
    private @Nullable Long createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
