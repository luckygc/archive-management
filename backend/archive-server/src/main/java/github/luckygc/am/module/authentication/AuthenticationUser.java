package github.luckygc.am.module.authentication;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import github.luckygc.am.common.audit.CreationTimeAuditable;
import github.luckygc.am.common.audit.UpdateTimeAuditable;

import lombok.Data;

@Data
@Entity
@Table(name = "am_authentication_user")
public class AuthenticationUser implements CreationTimeAuditable, UpdateTimeAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String username;

    @Column(nullable = false, length = 500)
    private String password;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Column private String email;

    @Column(name = "mobile_phone", length = 50)
    private String mobilePhone;

    @Column(name = "department_id")
    private Long departmentId;

    private boolean enabled = true;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
