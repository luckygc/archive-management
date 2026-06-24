/**
 * 运行时能力的业务无关合同。
 *
 * <p>这里仅声明数据库、队列、锁、会话、调度等能力的稳定接口和配置错误类型，不绑定 Spring、MyBatis、Quartz、Redis
 * 等具体基础设施实现。业务模块只能依赖这些抽象能力，具体 adapter 的选择和 fail-fast 校验放在 infrastructure 层完成。
 */
package github.luckygc.am.common.runtime;
