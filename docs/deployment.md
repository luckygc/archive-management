# 部署手册

本文面向测试、预生产和生产环境。系统运行面为 Spring Boot 主应用、PC 前端静态资源和独立文件预览服务；PostgreSQL 与 S3 兼容对象存储由部署环境提供。

## 部署组件

| 组件 | 构建来源 | 部署形态 |
| --- | --- | --- |
| 主应用 | `backend/archive-server/` | Spring Boot JAR |
| PC 前端 | `frontend/admin/` | 静态资源；`frontend/packages/core/` 随前端构建，不独立部署 |
| 文件预览服务 | `backend/preview-service/` | 独立 Go HTTP 服务 |
| 数据库 | 外部 PostgreSQL | 项目唯一优先数据库目标 |
| 文件内容 | 外部 S3 兼容对象存储 | 业务统一通过 `FileStorageService` 访问 |

## 构建与验证

构建发布产物：

```bash
task server-package
task frontend-build
task preview-build
```

发布前按范围运行 `task server-test`、`task frontend-ready`、`task preview-test`，并运行 `task governance-check`。所有任务均以根 [`Taskfile.yml`](../Taskfile.yml) 为准。

三个可部署应用分别拥有自己的容器构建定义。需要构建镜像时从仓库根目录执行：

```bash
docker build -f backend/archive-server/Dockerfile --target server .
docker build -f frontend/admin/Dockerfile --target web .
docker build backend/preview-service
```

前两个构建使用仓库根作为上下文，以读取统一工具版本和 `frontend/` 工作区；预览服务只使用自身目录作为上下文。

## 配置来源

主应用默认配置真相源是 [`application.yaml`](../backend/archive-server/src/main/resources/application.yaml)。部署环境不修改仓库默认文件，通过 Spring Boot 标准优先级使用外部 `application.yaml`、profile 配置、环境变量、JVM 参数以及 Secret/ConfigMap 覆盖。

必须外部提供或确认：

- PostgreSQL 地址、数据库、用户名、密码和连接池容量。
- S3 endpoint、region、bucket、access key、secret key 和 path-style 行为。
- 可信前端 Origin、请求签名策略、管理员初始化策略和 Actuator 暴露范围。
- 预览服务监听地址、上传限制和可选外部转换工具。

密钥、生产连接串、客户环境参数和管理员口令不得写入 Git。

## PostgreSQL 与迁移

项目自有 SQL 和迁移只面向 PostgreSQL。Flyway 随主应用启动执行结构迁移；共享环境必须保持 `spring.flyway.clean-disabled=true`，不得启用 `archive.flyway.clean-before-migrate`。

Spring Session、Quartz 和 Flowable 原生表由仓库 Flyway 迁移管理并保留上游命名；Spring Modulith JDBC Event Publication Registry 按当前框架配置初始化。具体表名和开关以 `application.yaml` 与迁移脚本为准。

发布前备份数据库并核对迁移兼容性；应用启动后先确认迁移成功，再放入业务流量。

## S3 兼容对象存储

文件内容只部署到成熟的外部 S3 兼容服务。示例外部配置：

```yaml
archive:
    storage:
        endpoint: https://s3-compatible.example.com
        region: us-east-1
        bucket: archive
        access-key: ${ARCHIVE_STORAGE_ACCESS_KEY}
        secret-key: ${ARCHIVE_STORAGE_SECRET_KEY}
        path-style-access: false
```

接入前验证签名、path-style、Put/Get/Head/Delete 和 multipart 行为。对象存储的高可用、版本控制、备份和异地灾备由部署环境负责。更换 endpoint 或 bucket 前先迁移并校验对象，再切换应用配置；不要把仓库 Compose 的单节点开发服务用于生产。

## 安全与初始化

生产环境必须把 CORS Origin 改为实际可信前端地址，并按风险决定是否启用请求签名；启用时通过外部 Secret 提供至少 32 个字符的密钥。Actuator 不向公网暴露全部端点，并结合监控角色、网关、网络策略或安全组限制来源。完整配置指引见 [`security.md`](security.md)。

管理员初始化默认关闭。首次部署可在受控窗口临时启用 `archive.authentication.bootstrap-admin.enabled` 并通过外部 Secret 提供密码；初始化完成后立即关闭。

## 部署顺序

1. 准备 PostgreSQL、备份策略和 S3 兼容 bucket。
2. 配置主应用、前端和预览服务的外部参数与 Secret。
3. 启动主应用，确认 Flyway 成功并检查 `/actuator/health` 与日志。
4. 部署前端静态资源，确认 API 地址、Cookie 和 CORS 策略一致。
5. 部署预览服务，检查 `/healthz` 和 `/v1/capabilities`。
6. 使用受控管理员账号检查登录、当前用户、权限和基础业务入口。

## 回滚原则

- 代码回滚前确认数据库迁移是否兼容旧版本；生产环境不使用 Flyway clean 回滚。
- 当前项目未正式发布前，迁移脚本按目标结构维护；正式发布后不得修改已经发布的迁移历史。
- 数据库与 S3 对象状态一起评估和恢复，避免记录与文件内容不一致。
- 保留发布产物、配置版本、数据库备份和对象存储恢复点，使回滚路径可验证。
