package github.luckygc.am.module.archive.metadata.repository;

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

import github.luckygc.am.module.archive.metadata.ArchiveFieldLayout;
import github.luckygc.am.module.archive.metadata.ArchiveLayoutSurface;

@Transactional(rollbackFor = Throwable.class, isolation = Isolation.READ_COMMITTED)
@Repository
public interface ArchiveFieldLayoutDataRepository {

    @Insert
    ArchiveFieldLayout insert(@Nonnull ArchiveFieldLayout entity);

    @Update
    ArchiveFieldLayout update(@Nonnull ArchiveFieldLayout entity);

    @Delete
    void delete(@Nonnull ArchiveFieldLayout entity);

    @Transactional(readOnly = true)
    @Find
    @OrderBy("rowOrder")
    @OrderBy("colOrder")
    @OrderBy("id")
    List<ArchiveFieldLayout> list(@Nonnull Long categoryId, @Nonnull ArchiveLayoutSurface surface);
}
