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

import org.jspecify.annotations.Nullable;

import github.luckygc.am.common.audit.CreationAuditable;
import github.luckygc.am.common.audit.UpdateAuditable;

import lombok.Data;

@Data
@Entity
@Table(name = "am_archive_physical_transfer_item")
public class ArchivePhysicalTransferItem implements CreationAuditable, UpdateAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transfer_id", nullable = false)
    private Long transferId;

    @Column(name = "physical_object_id", nullable = false)
    private Long physicalObjectId;

    @Column(name = "archive_item_id")
    private @Nullable Long archiveItemId;

    @Column(name = "archive_volume_id")
    private @Nullable Long archiveVolumeId;

    @Column(name = "barcode_snapshot", length = 120)
    private @Nullable String barcodeSnapshot;

    @Column(name = "carrier_type_snapshot", length = 80)
    private @Nullable String carrierTypeSnapshot;

    @Column(name = "quantity_snapshot", precision = 18, scale = 4)
    private @Nullable BigDecimal quantitySnapshot;

    @Column(name = "quantity_unit_snapshot", length = 30)
    private @Nullable String quantityUnitSnapshot;

    @Column(name = "condition_note_snapshot", length = 255)
    private @Nullable String conditionNoteSnapshot;

    @Column(name = "active_flag", nullable = false)
    private boolean activeFlag = true;

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
