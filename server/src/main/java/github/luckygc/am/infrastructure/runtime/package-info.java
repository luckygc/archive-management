/**
 * 运行时能力 adapter 的基础设施实现。
 *
 * <p>本包负责把 {@code archive.runtime.*} 配置解析成实际的数据库、队列、锁、会话、调度和缓存实现，并在应用启动时校验部署拓扑与
 * adapter 组合是否可用。业务模块不要直接依赖本包类型。
 */
package github.luckygc.am.infrastructure.runtime;
