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

该任务实际执行 `pnpm exec vp install`，由 Vite+ 管理前端依赖安装。拉取远程变更后、开始开发前也应重新运行一次。

3. 准备 PostgreSQL 数据库：

```sql
create database archive_management;
```

默认连接为 `jdbc:postgresql://localhost:5433/archive_management`，用户名为 `postgres`。本机端口、用户名或密码不同的，使用 `server/src/main/resources/application-local.yaml` 覆盖，不提交该文件。

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
- 管理员初始化只用于本地初始化或受控部署初始化；启用时必须通过外部配置提供密码。
- `db/sample` 会导入样例数据，只适合本地演示或测试环境。
- Flyway `clean` 默认禁用；本地重建库时优先重建数据库，不在共享环境开启 clean。

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
| 前端命令找不到 `vp` | 先运行 `task frontend-install`，直接调用时使用 `pnpm exec vp ...` |
| 后端启动时找不到 Spring Session 或 Quartz 表 | 确认 Flyway 已启用且迁移脚本位置包含 `classpath:db/migration` |
| PostgreSQL 连接失败 | 检查端口、数据库名、用户名和 `application-local.yaml` |
| 测试依赖数据库失败 | 确认本机 Docker/Testcontainers 或 PostgreSQL 环境满足测试要求 |
| 前端 401 跳登录 | 确认后端已启动、浏览器是否收到并回传 `am_session` session cookie，且 CORS 允许当前前端 Origin |

