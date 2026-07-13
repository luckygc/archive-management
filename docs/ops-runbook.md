# 运维手册

本文提供运行期检查、排障和恢复路径。配置详情见 `deployment.md`，数据库策略见 `database.md`，安全策略见 `security.md`。

## 健康检查

主应用：

```bash
curl http://localhost:8080/actuator/health
```

预览服务：

```bash
curl http://localhost:8088/healthz
curl http://localhost:8088/v1/capabilities
```

PC 前端：

- 浏览器能访问前端入口。
- 登录页能创建 CAP challenge。
- 登录成功后 `GET /api/v1/me` 返回当前主体。

## 日志位置

主应用默认日志目录：

```yaml
logging:
    file:
        path: logs
```

排障时优先查：

- 应用启动日志。
- Flyway 迁移日志。
- Spring Security 认证和授权日志。
- MyBatis SQL 错误。
- 文件存储异常。
- `X-Trace-Id` 对应的请求链路。

## 启动失败

| 现象 | 优先检查 |
| --- | --- |
| 数据库连接失败 | `spring.datasource.url`、端口、账号、密码、网络策略 |
| Flyway 校验失败 | 已执行迁移和当前脚本是否不一致，是否修改过已执行脚本 |
| Spring Session 表缺失 | `classpath:db/migration` 是否启用，迁移是否执行成功 |
| Quartz 表缺失 | `spring.quartz.job-store-type=jdbc` 时确认 `QRTZ_*` 已迁移 |
| Flowable 表缺失 | 确认 Flowable 迁移脚本已执行，且 `database-schema-update=false` |
| JDK 版本错误 | 后端要求 JDK 25 |
| 对象存储启动失败 | endpoint、bucket、access-key、secret-key、path-style 配置 |

## 登录和会话问题

处理顺序：

1. 检查浏览器是否收到 `am_session` Cookie。
2. 检查前端 Origin 是否在 CORS 白名单。
3. 检查 `POST /api/v1/cap-challenges`、`POST /api/v1/cap-tokens` 和 `POST /api/v1/login-sessions`。
4. 检查账号是否启用、密码是否正确、登录失败限制是否触发。
5. 管理员可查看登录会话和认证审计，并按需踢下线或重置失败限制。

登录失败限制重置接口：

```http
POST /api/v1/login-failure-limits/{username}:reset
```

## 权限问题

排查顺序：

1. `GET /api/v1/me` 确认当前用户。
2. `GET /api/v1/me/permissions` 确认功能权限点。
3. 检查用户角色绑定和角色是否启用。
4. 检查角色或用户绑定的数据范围。
5. 对档案列表、搜索、导出、电子文件下载等能力，确认后端是否命中全宗、分类和动态字段数据范围。

不要只通过前端按钮显隐判断权限是否生效。权限和数据范围必须由后端守住。

## 文件上传和下载问题

上传失败优先检查：

- `spring.servlet.multipart.max-file-size` 和 `max-request-size`。
- 当前用户是否有档案条目访问权限。
- `archive.storage` 的 endpoint、region、bucket 和凭证是否正确。
- S3 兼容对象存储的 bucket、网络和 path-style 配置是否可用。

下载失败优先检查：

- 文件记录是否存在且未删除。
- `bucket_name`、object key 是否与当前对象存储配置一致并能定位文件。
- 短链是否过期。
- 私有短链 `/api/v1/file-links/{code}:download` 是否已登录。
- 公开短链 `/api/v1/public-file-links/{code}:download` 是否使用公开下载路径。

## 数据库迁移问题

处理原则：

- 生产环境不要修改已经执行过的迁移脚本。
- Flyway 校验失败时先比对 `flyway_schema_history` 和当前脚本校验和。
- 项目自有表使用小写 snake_case 和 `am_模块_表名`。
- 第三方表保留 `SPRING_SESSION`、`QRTZ_*`、`ACT_*` 等原生命名。
- 动态分类表由档案元数据服务创建和维护，禁止手工补列后不回写字段定义。

## 备份和恢复

最小备份集合：

- PostgreSQL 数据库。
- 对象存储 bucket 内容。
- 部署配置和密钥管理系统中的配置版本。
- 前端和后端发布包版本。

恢复时先恢复数据库，再恢复文件内容，最后启动应用执行兼容检查。数据库和文件存储必须保持同一时间点或明确可接受的恢复点差异。

## 性能和容量观察

主应用重点关注：

- PostgreSQL 连接池等待时间。
- 档案搜索和导出查询耗时。
- 文件上传大小和下载带宽。
- Caffeine nonce 缓存大小。
- Spring Session 表增长。
- 认证审计、档案审计和规则追踪表增长。

预览服务重点关注：

- 上传文件大小。
- Magika 探测耗时。
- 同步转换接口响应时间。
- 外部转换工具是否缺失。

## 应急处理清单

1. 保存错误响应中的 `traceId`、时间、用户和请求路径。
2. 查主应用日志和数据库错误。
3. 判断问题属于认证、权限、数据库迁移、SQL、文件存储、外部服务还是前端状态。
4. 对写入失败确认事务是否回滚、文件是否已经写入存储。
5. 对部分成功场景记录受影响档案、文件和用户。
6. 修复后运行对应验证命令，并补充回归测试或文档说明。
