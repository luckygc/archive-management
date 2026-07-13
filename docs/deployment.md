# 部署手册

本文面向测试、预生产和生产环境部署。项目当前由三个运行面组成：Spring Boot 主应用、PC 前端静态资源、独立文件预览服务。

## 部署组件

| 组件 | 目录 | 运行方式 | 说明 |
| --- | --- | --- | --- |
| 主应用 | `server/` | Spring Boot JAR | 提供 `/api/v1/**`、静态资源、安全、会话、迁移和业务能力 |
| PC 前端 | `web/` | 构建为静态资源 | React + Ant Design PC 管理界面 |
| 前端共享包 | `frontend-core/` | 构建产物被 `web` 消费 | 不独立部署 |
| 文件预览服务 | `preview/` | 独立 Go HTTP 服务 | 提供同步预览转换能力，默认端口 `8088` |
| PostgreSQL | 外部服务 | 数据库 | 唯一优先数据库目标 |
| 对象存储 | 外部 S3 兼容服务 | 文件内容存储 | 生产使用成熟对象存储，开发 Compose 提供单节点端点 |

## 构建

后端打包：

```bash
task server-package
```

前端构建：

```bash
task frontend-build
```

预览服务构建：

```bash
task preview-build
```

发布前建议至少运行：

```bash
task server-test
task frontend-ready
task preview-test
```

## 配置来源

主应用默认配置在 `server/src/main/resources/application.yaml`。部署环境不要修改该文件，应通过以下方式覆盖：

- 外部 `application.yaml` 或 `application-<profile>.yaml`。
- 环境变量。
- 容器编排平台的 Secret 和 ConfigMap。
- JVM 参数或 Spring Boot 标准配置优先级。

不得把密钥、数据库密码和对象存储密钥写入 Git。

## 必需外部依赖

### PostgreSQL

默认配置：

```yaml
spring:
    datasource:
        url: jdbc:postgresql://localhost:5433/archive_management
        username: postgres
        password:
```

生产环境必须显式配置数据库地址、用户名、密码和连接池容量。项目自有 SQL、迁移脚本和查询按 PostgreSQL 优先设计，不维护其他数据库兼容分支。

### Flyway

结构迁移默认开启：

```yaml
spring:
    flyway:
        enabled: true
        locations:
            - classpath:db/migration
        validate-on-migrate: true
        clean-disabled: true
```

发布时由应用启动过程执行迁移。共享环境保持 `clean-disabled=true`，不要开启 `archive.flyway.clean-before-migrate`。

### 会话、调度和流程表

- Spring Session 表名为 `SPRING_SESSION`，由 Flyway 管理。
- Quartz 表名为 `QRTZ_*`，由 Flyway 管理。
- Flowable 原生 `ACT_*` 表由 Flyway 管理，运行期 `flowable.database-schema-update=false`。
- Spring Modulith 事件发布注册表由框架 JDBC 初始化管理。

这些第三方表保留上游命名，不改成 `am_` 前缀。

## 存储配置

文件内容统一使用 S3 兼容对象存储：

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

生产建议：

- 使用 AWS S3、OSS、COS、OBS、Ceph RGW 等成熟外部 S3 兼容服务，不把开发 Compose 的单节点服务用于生产。
- endpoint 仅在 AWS S3 SDK 默认地址不适用时配置；接入前验证 path-style、签名、Put/Get/Head/Delete 和 multipart 行为。
- 文件记录中的 `bucket_name` 和 object key 是下载、删除、审计的依据。
- 更换 endpoint 或 bucket 前必须先迁移并校验文件内容，再切换应用配置；应用不同时挂载新旧存储。
- 对象存储自身的高可用、版本控制、备份和异地灾备由部署环境负责。

## 安全配置

生产环境至少确认：

```yaml
archive:
    security:
        cors:
            allowed-origins:
                - https://your-web-origin.example
        request-signature:
            enabled: true
            secret: "<至少 32 个字符的密钥>"
```

说明：

- `/api/**` 默认要求登录，登录、CAP 和公开文件短链除外。
- 请求签名开启后，除 OPTIONS 预检外的 `/api/**` 请求必须携带 `X-AM-Timestamp`、`X-AM-Nonce` 和 `X-AM-Signature`。
- Actuator 端点需要 `系统监控` 角色，角色名可通过 `archive.security.authorization.actuator-role-name` 覆盖。
- 生产环境不应暴露全部 Actuator 端点给公网。

## 管理员初始化

管理员初始化默认关闭：

```yaml
archive:
    authentication:
        bootstrap-admin:
            enabled: false
```

首次部署可在受控窗口临时开启，并通过外部 Secret 提供密码。初始化完成后关闭该开关，避免重复初始化口令成为长期风险。

## 部署顺序

1. 准备 PostgreSQL、存储目录或对象存储 bucket。
2. 配置主应用外部配置和密钥。
3. 启动主应用，让 Flyway 完成迁移。
4. 检查 `/actuator/health` 和日志。
5. 部署前端静态资源，确认后端 API 地址、Cookie 和 CORS 策略一致。
6. 部署预览服务，检查 `/healthz` 和 `/v1/capabilities`。
7. 使用管理员账号登录 PC 端，检查工作台、当前用户、权限和基础字典。

## 回滚原则

- 代码回滚前先确认数据库迁移是否可兼容旧版本。
- 当前项目未正式发布前，迁移脚本按目标结构直接维护；进入正式发布后，已发布迁移不得修改历史内容。
- 文件存储和数据库状态要一起评估，避免数据库回滚后文件内容孤立或短链失效。
- 生产环境不通过 Flyway clean 回滚。
