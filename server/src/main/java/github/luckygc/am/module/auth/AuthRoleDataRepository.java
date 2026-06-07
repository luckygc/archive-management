package github.luckygc.am.module.auth;

import jakarta.data.page.Page;
import jakarta.data.page.PageRequest;
import jakarta.data.repository.BasicRepository;
import jakarta.data.repository.Find;
import jakarta.data.repository.OrderBy;
import jakarta.data.repository.Repository;
import java.awt.print.Pageable;
import java.util.Optional;
import org.hibernate.StatelessSession;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Transactional(rollbackFor = Throwable.class,isolation = Isolation.READ_COMMITTED)
@Repository
public interface AuthRoleDataRepository extends BasicRepository<AuthRole, Long> {

    @Find
    Optional<AuthRole> findByRoleName(String roleName);

    @Find
    @OrderBy("id")
    Page<AuthRole> findAll(PageRequest pageRequest);
}
