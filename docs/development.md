# 本地开发手册

本文面向本地开发、运行和验证。除明确标注外，命令均从仓库根目录执行；后端 Maven 项目根目录是 `server/`，仓库根目录没有聚合 POM。真实任务入口以 [`Taskfile.yml`](../Taskfile.yml)、各 `package.json` 和构建配置为准。

## 工具版本

[`mise.toml`](../mise.toml) 固定本仓常用工具：

| 工具 | 版本 |
| --- | --- |
| Java | 25 |
| Maven | 3 |
| Node.js | 24 |
| pnpm | 11 |
| Task | 3 |

优先通过 `task` 执行仓库任务；需要直接调用工具时使用 `mise exec -- <command>`。根 `package.json` 声明 Node.js 最低版本为 `>=22.12.0`。

## 首次准备

拉取远程变更后、开始开发前安装或刷新前端依赖：

```bash
task frontend-install
```

启动本地 PostgreSQL、S3 兼容对象存储并初始化开发 bucket：

```bash
task infra-up
```

该任务由 `compose.yaml` 和 `Taskfile.yml` 定义，等待两个服务健康后通过 AWS SigV4 幂等创建 bucket。本地默认端口、账号和临时数据策略以这两个文件为准；Compose 环境只用于开发，不提供生产持久化、高可用或灾备。

已有 PostgreSQL 和 S3 兼容服务时，无需启动 Compose，可通过本机覆盖配置连接现有服务。停止仓库提供的本地基础设施使用：

```bash
task infra-down
```

## 本机覆盖配置

[`server/src/main/resources/application.yaml`](../server/src/main/resources/application.yaml) 可选导入 classpath 下的 `application-local.yaml`。该文件只用于本机差异，不是交付或部署真相源，也不得提交密钥。

最小本机覆盖示例：

```yaml
spring:
    datasource:
        password: postgres
    flyway:
        locations:
            - classpath:db/migration
            - classpath:db/sample

archive:
    authentication:
        bootstrap-admin:
            enabled: true
            password: change-me-local-only
```

`db/sample` 只用于本地演示或测试。管理员初始化只在本地初始化或受控部署窗口启用；共享环境不启用 Flyway clean。部署环境通过 Spring Boot 标准外部配置提供数据库、S3 endpoint、bucket 和密钥，详见 [`deployment.md`](deployment.md)。

## 运行入口

Spring Boot 主应用：

```bash
task server-run
```

PC 前端开发服务：

```bash
task web-dev
```

`task web-dev` 会长期占用端口，只由开发者在需要预览时本地执行；自动化代理不主动启动。

独立文件预览服务：

```bash
task preview-run
```

默认端口和运行参数分别以 `application.yaml`、Vite+ 配置和 [`preview/README.md`](../preview/README.md) 为准，本文不复制运行参数表。

## 按范围验证

| 改动范围 | 真实入口 |
| --- | --- |
| 当前规范、OpenSpec 或工程文档 | `task governance-check` |
| 全部前端包 | `task frontend-check`、`task frontend-test`；影响构建时运行 `task frontend-build` |
| 单个前端包 | `task web-*` 或 `task frontend-core-*` 对应任务 |
| 后端 Java | `task server-format-check`、`task server-compile`、相关 `task server-test` |
| 后端发布包 | `task server-package` |
| 文件预览服务 | `task preview-test`、`task preview-build` |

后端需要直接运行 Maven 时，先 `cd server` 再执行 Maven 命令。前端直接调用 Vite+ 时使用项目依赖提供的 `pnpm exec vp ...`；可用子命令以 `pnpm exec vp help` 为准。

## 工具链排障

环境或包管理行为异常时保留以下输出：

```bash
mise doctor
pnpm --version
pnpm exec vp --version
```

后端启动提示 Spring Session、Quartz 或 Flowable 表缺失时，先检查 Flyway 是否启用及结构迁移位置；测试依赖数据库失败时，确认 Docker/Testcontainers 或外部 PostgreSQL 环境可用。运行期配置项以 `application.yaml` 为准。
