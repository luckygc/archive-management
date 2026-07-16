package github.luckygc.am.module.archive.rule;

import java.time.LocalDateTime;
import java.util.Map;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SoftDelete;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;

import github.luckygc.am.common.audit.CreationAuditable;
import github.luckygc.am.common.audit.UpdateAuditable;

import lombok.Data;

@Data
@Entity
@Table(name = "am_archive_rule_effect")
@SoftDelete(columnName = "deleted_flag")
public class ArchiveRuleEffect implements CreationAuditable, UpdateAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "rule_id", nullable = false)
    private Long ruleId;

    @Column(name = "effect_type", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private ArchiveRuleEffectType effectType;

    @Column(name = "effect_order", nullable = false)
    private int effectOrder;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "effect_params", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> effectParams = Map.of();

    @Column(name = "created_by")
    private @Nullable Long createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_by")
    private @Nullable Long updatedBy;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
