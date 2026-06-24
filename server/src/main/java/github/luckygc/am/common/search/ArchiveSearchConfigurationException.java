package github.luckygc.am.common.search;

/** 搜索能力配置不满足启动要求时抛出的启动期异常。 */
public class ArchiveSearchConfigurationException extends RuntimeException {

    /** 给运维或开发者看的修复建议，用于 Spring Boot failure analyzer 输出。 */
    private final String action;

    public ArchiveSearchConfigurationException(String message, String action) {
        super(message);
        this.action = action;
    }

    public ArchiveSearchConfigurationException(String message, String action, Throwable cause) {
        super(message, cause);
        this.action = action;
    }

    public String action() {
        return action;
    }
}
