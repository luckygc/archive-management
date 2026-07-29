package github.luckygc.am.module.archive.physical;

import java.math.BigDecimal;
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
@Table(name = "am_archive_physical_object")
@SoftDelete(columnName = "deleted_flag")
public class ArchivePhysicalObject implements CreationAuditable, UpdateAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "archive_item_id")
    private @Nullable Long archiveItemId;

    @Column(name = "archive_volume_id")
    private @Nullable Long archiveVolumeId;

    @Column(length = 120)
    private @Nullable String barcode;

    @Column(name = "carrier_type", length = 80)
    private @Nullable String carrierType;

    @Column(precision = 18, scale = 4)
    private @Nullable BigDecimal quantity;

    @Column(name = "quantity_unit", length = 30)
    private @Nullable String quantityUnit;

    @Column(name = "condition_note")
    private @Nullable String conditionNote;

    @Column(name = "current_location_id")
    private @Nullable Long currentLocationId;

    @Column(length = 1000)
    private @Nullable String remark;

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
