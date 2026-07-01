package github.luckygc.am.common.repository;

import java.util.List;
import java.util.Optional;

import jakarta.annotation.Nonnull;
import jakarta.data.repository.By;
import jakarta.data.repository.Delete;
import jakarta.data.repository.Find;
import jakarta.data.repository.Insert;
import jakarta.data.repository.Update;

public interface DataRepository<T, ID> extends jakarta.data.repository.DataRepository<T, ID> {

    @Insert
    <S extends T> S insert(@Nonnull S entity);

    @Insert
    <S extends T> List<S> insertAll(@Nonnull List<S> entities);

    @Update
    <S extends T> S update(@Nonnull S entity);

    @Update
    <S extends T> List<S> updateAll(@Nonnull List<S> entities);

    @Find
    Optional<T> findById(@By(By.ID) @Nonnull ID id);

    @Delete
    void delete(@Nonnull T entity);

    @Delete
    void deleteAll(@Nonnull List<? extends T> entities);
}
