package github.luckygc.am.infrastructure.flyway;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "archive.flyway")
public class FlywayResetProperties {

    /**
     * 启动迁移前是否先执行 Flyway clean。仅允许本地重建或演示环境临时开启。
     */
    private boolean cleanBeforeMigrate;

    public boolean isCleanBeforeMigrate() {
        return cleanBeforeMigrate;
    }

    public void setCleanBeforeMigrate(boolean cleanBeforeMigrate) {
        this.cleanBeforeMigrate = cleanBeforeMigrate;
    }
}
