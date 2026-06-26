/**
 * 跨业务模块共享的应用基础约定。
 *
 * <p>本包只放不承载具体业务语义、也不绑定某个外部技术适配的通用能力，例如 API
 * 合同类型、基础异常模型、通用校验和少量全局配置。仅被单个业务模块使用的 DTO、工具类、
 * 查询对象、枚举和 service 不应放入本包，应留在对应业务模块内。
 *
 * <p>认证登录、用户身份和权限语义属于业务边界，放在 {@code github.luckygc.am.module.auth}
 * 或后续独立用户/权限模块；Spring Security、文件存储、外部系统客户端、持久化适配等
 * 技术实现放在 {@code github.luckygc.am.infrastructure} 下的具体子包。
 */
@NullMarked
package github.luckygc.am.common;

import org.jspecify.annotations.NullMarked;
