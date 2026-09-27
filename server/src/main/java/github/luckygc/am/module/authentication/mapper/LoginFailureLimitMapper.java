package github.luckygc.am.module.authentication.mapper;

import java.time.LocalDateTime;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.jspecify.annotations.Nullable;

import github.luckygc.am.module.authentication.LoginFailureLimit;

@Mapper
public interface LoginFailureLimitMapper {

    int insertFirstFailureIfAbsent(
            @Param("username") String username,
            @Param("now") LocalDateTime now,
            @Param("cleanupAfter") LocalDateTime cleanupAfter);

    @Nullable LoginFailureLimit findByUsernameForUpdate(@Param("username") String username);

    int update(@Param("limit") LoginFailureLimit limit);
}
