package github.luckygc.am.infrastructure.runtime;

import org.springframework.boot.context.properties.ConfigurationProperties;

import github.luckygc.am.common.runtime.DeploymentTopology;

import lombok.Getter;
import lombok.Setter;

/** 运行时能力配置，统一描述单机和集群部署需要选择的 adapter。 */
@Getter
@Setter
@ConfigurationProperties(prefix = "archive.runtime")
public class RuntimeCapabilityProperties {

    /** 当前部署拓扑；集群部署会触发共享能力校验。 */
    private DeploymentTopology topology = DeploymentTopology.single;

    /** 当前节点 ID，用于队列 worker、锁持有者和调度诊断。 */
    private String nodeId = "archive-management";

    private Database database = new Database();

    private Queue queue = new Queue();

    private Lock lock = new Lock();

    private Cache cache = new Cache();

    private Storage storage = new Storage();

    /** 数据库运行时配置。 */
    @Getter
    @Setter
    public static class Database {

        /** 数据库 adapter 名称，当前内置 {@code postgresql}。 */
        private String adapter = "postgresql";
    }

    /** 队列运行时配置。 */
    @Getter
    @Setter
    public static class Queue {

        /** 队列 adapter 名称，当前内置 {@code database}。 */
        private String adapter = "database";

        /** 主队列 adapter 未注册时的降级 adapter；留空表示不降级。 */
        private String fallbackAdapter = "database";

        /** 单条消息最大认领尝试次数，达到后进入死信状态。 */
        private int maxAttempts = 10;
    }

    /** 锁运行时配置。 */
    @Getter
    @Setter
    public static class Lock {

        /** 锁 adapter 名称，当前内置 {@code database} 和 {@code local}。 */
        private String adapter = "database";

        /** 主锁 adapter 未注册时的降级 adapter；留空表示不降级。 */
        private String fallbackAdapter = "database";
    }

    /** Spring Cache 集群安全校验配置。 */
    @Getter
    @Setter
    public static class Cache {

        /** 自定义或 generic CacheManager 是否由共享介质承载；仅集群部署且无法自动判断时使用。 */
        private boolean shared;
    }

    /** 文件存储运行时校验配置。 */
    @Getter
    @Setter
    public static class Storage {

        /** 本地存储是否由外部共享目录承载；集群部署使用 local 存储时必须为 true。 */
        private boolean localShared;
    }
}
