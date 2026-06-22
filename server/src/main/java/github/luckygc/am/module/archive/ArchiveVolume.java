package github.luckygc.am.module.archive;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import lombok.Data;

@Data
@Entity
@Table(name = "am_archive_volume")
public class ArchiveVolume {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "fonds_code", nullable = false, length = 100)
    private String fondsCode;

    @Column(name = "fonds_name", nullable = false)
    private String fondsName;

    @Column(name = "category_code", nullable = false, length = 100)
    private String categoryCode;

    @Column(name = "category_name", nullable = false)
    private String categoryName;

    @Column(name = "volume_no", nullable = false, length = 100)
    private String volumeNo;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "deleted_flag", nullable = false)
    private boolean deletedFlag;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", nullable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "updated_at", nullable = false)
    @UpdateTimestamp
    private LocalDateTime updatedAt;

}
