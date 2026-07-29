package github.luckygc.am.module.intake;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.jspecify.annotations.Nullable;

import github.luckygc.am.common.audit.CreationTimeAuditable;

import lombok.Data;

@Data
@Entity
@Table(name = "am_archive_intake_package_item")
public class ArchiveIntakePackageItem implements CreationTimeAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "intake_package_id", nullable = false)
    private Long intakePackageId;

    @Column(name = "archive_item_id", nullable = false)
    private Long archiveItemId;

    @Column(name = "item_order", nullable = false)
    private int itemOrder;

    @Column(name = "fonds_code", nullable = false, length = 100)
    private String fondsCode;

    @Column(name = "category_code", nullable = false, length = 100)
    private String categoryCode;

    @Column(name = "archive_no")
    private @Nullable String archiveNo;

    @Column(name = "electronic_file_count", nullable = false)
    private int electronicFileCount;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
