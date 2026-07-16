package github.luckygc.am.module.organization.repository;

import java.util.List;
import java.util.Optional;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.data.repository.By;
import jakarta.data.repository.Find;
import jakarta.data.repository.Insert;
import jakarta.data.repository.OrderBy;
import jakarta.data.repository.Param;
import jakarta.data.repository.Repository;
import jakarta.data.repository.Update;

import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import github.luckygc.am.module.organization.OrganizationDepartment;

@Transactional(rollbackFor = Throwable.class, isolation = Isolation.READ_COMMITTED)
@Repository
public interface OrganizationDepartmentDataRepository {

    @Find
    Optional<OrganizationDepartment> findById(@By(By.ID) @Nonnull Long id);

    @Insert
    OrganizationDepartment insert(@Nonnull OrganizationDepartment entity);

    @Update
    OrganizationDepartment update(@Nonnull OrganizationDepartment entity);

    @Transactional(readOnly = true)
    @Find
    @OrderBy("sortOrder")
    @OrderBy("id")
    List<OrganizationDepartment> list();

    @Transactional(readOnly = true)
    @Find
    @OrderBy("sortOrder")
    @OrderBy("id")
    List<OrganizationDepartment> list(@Param("enabled") @Nullable Boolean enabled);

    @Transactional(readOnly = true)
    @Find
    @Nullable OrganizationDepartment findByDepartmentCode(@Nonnull String departmentCode);
}
