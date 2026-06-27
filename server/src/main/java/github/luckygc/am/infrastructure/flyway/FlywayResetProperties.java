package github.luckygc.am.infrastructure.flyway;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Setter
@Getter
@ConfigurationProperties(prefix = "archive.flyway")
public class FlywayResetProperties {

    /**
     * 启动迁移前是否先执行 Flyway clean。仅允许本地重建或演示环境临时开启。
     */
    private boolean cleanBeforeMigrate;

}
