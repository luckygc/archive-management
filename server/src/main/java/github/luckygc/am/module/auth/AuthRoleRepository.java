package github.luckygc.am.module.auth;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class AuthRoleRepository {

    private final AuthRoleDataRepository dataRepository;

    public AuthRoleRepository(AuthRoleDataRepository dataRepository) {
        this.dataRepository = dataRepository;
    }

    @Transactional(readOnly = true)
    public Optional<AuthRole> findById(Long id) {
        return dataRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public Optional<AuthRole> findByRoleName(String roleName) {
        return dataRepository.findByRoleName(roleName);
    }

    @Transactional(readOnly = true)
    public List<AuthRole> findAll() {
        return dataRepository.findAll().toList();
    }

    @Transactional
    public AuthRole save(AuthRole role) {
        return dataRepository.save(role);
    }

    @Transactional
    public void delete(AuthRole role) {
        dataRepository.delete(role);
    }
}
