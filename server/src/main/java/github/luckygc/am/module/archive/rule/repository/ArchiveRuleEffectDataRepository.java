package github.luckygc.am.module.archive.rule.repository;

import java.util.List;

import jakarta.annotation.Nonnull;
import jakarta.data.repository.Delete;
import jakarta.data.repository.Find;
import jakarta.data.repository.Insert;
import jakarta.data.repository.OrderBy;
import jakarta.data.repository.Repository;
import jakarta.data.repository.Update;

import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import github.luckygc.am.module.archive.rule.ArchiveRuleEffect;

@Transactional(rollbackFor = Throwable.class, isolation = Isolation.READ_COMMITTED)
@Repository
public interface ArchiveRuleEffectDataRepository {

    @Insert
    List<ArchiveRuleEffect> insertAll(@Nonnull List<ArchiveRuleEffect> entities);

    @Update
    ArchiveRuleEffect update(@Nonnull ArchiveRuleEffect entity);

    @Delete
    void delete(@Nonnull ArchiveRuleEffect entity);

    @Transactional(readOnly = true)
    @Find
    @OrderBy("effectOrder")
    @OrderBy("id")
    List<ArchiveRuleEffect> findByRuleId(@Nonnull Long ruleId);
}
