# 本地开发手册

本文面向本地开发和代码验证。仓库根目录是 `D:\dev\archive-management`；后端 Maven 项目根目录是 `server/`，仓库根目录没有聚合 POM。

## 工具版本

项目使用 `mise.toml` 固定常用工具：

| 工具 | 版本约束 | 用途 |
| --- | --- | --- |
| Java | `liberica-25` | 后端编译、测试和运行 |
| Maven | `3` | 后端依赖、编译、测试和打包 |
| Node.js | `24` | 前端工具链运行时 |
| pnpm | `11` | 前端包管理 |
| Task | `3` | 跨模块任务入口 |

根 `package.json` 声明的 Node 最低版本是 `>=22.12.0`，本仓默认按 `mise.toml` 使用 Node 24。直接运行命令时优先使用 `task`，或使用 `mise exec -- <command>` 进入同一工具环境。

## 首次准备

1. 安装工具链并确认版本：

```bash
task --version
mise exec -- java -version
mise exec -- mvn -version
mise exec -- pnpm --version
```

2. 安装前端依赖：

```bash
task frontend-install
```

该任务实际通过 mise 管理的项目级 pnpm 执行 `pnpm install`。拉取远程变更后、开始开发前也应重新运行一次。

3. 启动 PostgreSQL 和对象存储，并初始化开发 bucket：

```bash
task infra-up
```

该任务等待 `postgres` 和 `object-storage` 状态变为 `healthy`，然后使用 curl 的 AWS SigV4 支持幂等创建 `archive` bucket，因此本机需要 curl 7.75 或更高版本。Compose 默认创建 `archive_management` 数据库：

- PostgreSQL 宿主机端口为 `5433`，用户名和密码均为 `postgres`。
- S3 API 宿主机端口为 `9000`，控制台端口为 `9001`，开发账号和密码均为 `rustfsadmin`。
- PostgreSQL 和 RustFS 数据目录均使用 `tmpfs`，容器停止后数据不会保留。

Compose 中的 PostgreSQL 和单节点 RustFS 只用于原型阶段的本地开发，不提供数据持久化、生产高可用或灾备能力。重新启动环境后，后端通过 Flyway 重建数据库结构，`task infra-up` 重新创建 `archive` bucket。已有 PostgreSQL 和 S3 兼容服务也可以继续使用，通过本地配置覆盖连接信息即可。

端口和初始化账号可以在启动命令前通过 `POSTGRES_*`、`ARCHIVE_STORAGE_*` 环境变量覆盖。覆盖后必须同步调整后端配置。

## 本地配置

`application.yaml` 会导入 classpath 下的 `application-local.yaml`。本机常用覆盖示例：

```yaml
spring:
    datasource:
        url: jdbc:postgresql://localhost:5433/archive_management
        username: postgres
        password: postgres
    flyway:
        locations:
            - classpath:db/migration
            - classpath:db/sample

archive:
    storage:
        endpoint: http://localhost:9000
        region: us-east-1
        bucket: archive
        access-key: rustfsadmin
        secret-key: rustfsadmin
        path-style-access: true

    authentication:
        bootstrap-admin:
            enabled: true
            username: admin
            password: change-me-local-only
            display-name: 系统管理员
    flyway:
        clean-before-migrate: false
```

说明：

- `application-local.yaml` 被构建排除，不能作为交付配置来源。
- 使用 Compose 默认配置时，`spring.datasource.password` 应设置为 `postgres`。
- 使用 Compose 默认配置时，文件存储配置可直接使用 `application.yaml` 的开发默认值。
- 生产环境必须覆盖文件存储 endpoint、bucket 和凭证，不得使用开发账号。
- 管理员初始化只用于本地初始化或受控部署初始化；启用时必须通过外部配置提供密码。
- `db/sample` 会导入样例数据，只适合本地演示或测试环境。
- Flyway `clean` 默认禁用；本地重建库时优先重建数据库，不在共享环境开启 clean。

## 管理本地基础设施

查看容器状态：

```bash
docker compose ps
```

停止并删除容器：

```bash
task infra-down
```

PostgreSQL 和 RustFS 使用临时文件系统，执行 `docker compose down` 后本地数据库和文件对象均会丢失；下次启动会重新初始化，无需使用 `down -v` 清理数据卷。

## 运行后端

后端运行入口：

```bash
task server-run
```

等价于在 `server/` 下执行 `mvn spring-boot:run`。默认 HTTP 端口是 `8080`，健康检查入口是 `/actuator/health`。所有 Flyway 结构迁移在应用启动时执行。

常用后端验证命令：

```bash
task server-format-check
task server-compile
task server-test
task server-package
```

如果只需要 Maven 命令，必须以 `server/` 为工作目录：

```bash
cd server
mise exec -- mvn -q -DskipTests test-compile
```

## 运行前端

前端包：

- `frontend-core/`：认证、API client、CAP 校验和共享类型。
- `web/`：PC 主应用。

常用命令：

```bash
task frontend-check
task frontend-test
task frontend-build
```

PC 前端开发服务：

```bash
task web-dev
```

该命令会启动长期占用端口的开发服务。自动化代理或非交互检查不主动启动它；需要预览时由开发者本地执行。

## 运行文件预览服务

`preview/` 是独立 Go 服务，默认监听 `:8088`：

```bash
task preview-run
```

常用验证命令：

```bash
task preview-test
task preview-build
```

Magika 是可选探测器；未安装时服务会退回本地最小探测逻辑。详见 `../preview/README.md`。

## 推荐验证闭环

前端改动：

```bash
task frontend-check
task frontend-test
task frontend-build
```

后端改动：

```bash
task server-format-check
task server-compile
task server-test
```

预览服务改动：

```bash
task preview-test
task preview-build
```

全前端就绪检查：

```bash
mise exec -- pnpm ready
```

## 常见问题

| 现象 | 处理 |
| --- | --- |
| Maven 提示 Java 版本不符合 | 使用 `mise exec -- mvn ...`，确认 JDK 为 25 |
| 前端命令找不到 `vp` | 先运行 `task frontend-install`，直接调用时使用项目依赖提供的 `pnpm exec vp ...` |
| 后端启动时找不到 Spring Session 或 Quartz 表 | 确认 Flyway 已启用且迁移脚本位置包含 `classpath:db/migration` |
| PostgreSQL 连接失败 | 检查端口、数据库名、用户名和 `application-local.yaml` |
| 测试依赖数据库失败 | 确认本机 Docker/Testcontainers 或 PostgreSQL 环境满足测试要求 |
| 前端 401 跳登录 | 确认后端已启动、浏览器是否收到并回传 `am_session` session cookie，且 CORS 允许当前前端 Origin |
