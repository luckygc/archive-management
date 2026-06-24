package github.luckygc.am.common.runtime;

/** 应用部署拓扑，用于启动期校验本地能力是否可以用于当前部署形态。 */
public enum DeploymentTopology {
    /** 单节点部署，可以使用本地缓存、本地锁等进程内能力。 */
    single,
    /** 集群部署，必须使用共享队列、共享锁、共享会话和共享文件存储。 */
    cluster
}
