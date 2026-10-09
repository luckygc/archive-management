# 本地开发手册

本文面向本地开发、运行和验证。除明确标注外，命令均从仓库根目录执行；后端 Maven 项目根目录是 `server/`，前端工作区根目录是 `frontend/`，仓库根目录没有聚合 POM 或 pnpm 工作区。真实任务入口以 [`mise.toml`](../mise.toml)、各 `package.json` 和构建配置为准。

## 工具版本

[`mise.toml`](../mise.toml) 固定本仓常用工具：

| 工具 | 版本 |
| --- | --- |
| Java | 25 |
| Maven | 3 |
| Node.js | 24 |
| pnpm | 12.6.0 |

简单命令直接使用 Maven 或项目 pnpm 脚本，不在 mise 中重复包装。下面从仓库根目录执行的命令通过 `mise exec -- <command>` 使用仓库指定的工具版本；已激活 mise 的终端也可进入 `server/` 或 `frontend/` 直接执行。组合步骤或较长固定参数保留为 mise 任务，名称按 `:` 分组，可用 `mise tasks ls` 查看。[`frontend/package.json`](../frontend/package.json) 声明 Node.js 最低版本为 `>=22.12.0`。

## 首次准备

拉取远程变更后、开始开发前安装或刷新前后端依赖：

```bash
mise run install
```

该任务并行执行前端 `pnpm install` 和后端 `mvn dependency:resolve`；后端步骤下载项目依赖，不编译或打包应用。

启动本地 PostgreSQL 和 S3 兼容对象存储：

```bash
mise run infra:up
```

该任务由 [`deploy/compose.dev.yaml`](../deploy/compose.dev.yaml) 和 [`mise.toml`](../mise.toml) 定义。首次运行创建 PostgreSQL 与对象存储容器；再次运行会启动已有容器，等待两个服务健康。Compose 文件不配置数据卷；停止并重新启动同一容器时数据保留，删除或重建容器后数据不保证保留。PostgreSQL 官方镜像会自行创建匿名卷，因此不要使用 `docker compose down` 后期望下一次 `up` 找回原数据。Compose 环境只用于开发，不提供生产级备份、高可用或灾备。

已有 PostgreSQL 和 S3 兼容服务时，无需启动 Compose，可通过本机覆盖配置连接现有服务。停止仓库提供的本地基础设施使用：

```bash
mise run infra:stop
```

该命令只停止容器，不删除容器或数据；下次执行 `mise run infra:up` 会恢复原容器。

停止并删除本地容器及 Compose 网络使用：

```bash
mise run infra:down
```

`infra:down` 不附加 `--volumes`，但容器删除后，再次 `infra:up` 不保证恢复原数据；需要保留当前开发环境时使用 `infra:stop`。

## 本机覆盖配置

[`application.yaml`](../server/src/main/resources/application.yaml) 可选导入 classpath 下的 `application-local.yaml`。该文件只用于本机差异，不是交付或部署真相源，也不得提交密钥。

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
```

`db/sample` 只用于本地演示或测试。内置 `admin` 账号在首次启动且账号不存在时自动创建；未提供密码时生成随机初始密码，在数据库事务提交后输出到终端和应用日志，重启不会重复输出或重置密码。请及时保存初始密码并在首次登录后修改。共享环境不启用 Flyway clean。部署环境通过 Spring Boot 标准外部配置提供数据库、S3 endpoint、bucket 和密钥，详见 [`deployment.md`](deployment.md)。

需要在本机强制所有用户登录时绑定 TOTP，先配置 `archive.authentication.totp.required: true`，并为后端配置持久的 TOTP 加密主密钥。未绑定用户密码验证通过后，登录页展示密钥；保存到身份验证器并提交动态验证码后才可进入系统。

仅在临时数据库中测试时，可为当前 PowerShell 终端生成临时主密钥再启动后端：

```bash
$totpBytes = [byte[]]::new(32)
[System.Security.Cryptography.RandomNumberGenerator]::Fill($totpBytes)
$env:ARCHIVE_TOTP_ENCRYPTION_KEY = [Convert]::ToBase64String($totpBytes)
mise exec -- mvn -f server/pom.xml spring-boot:run
```

该变量只作用于当前终端，不写入仓库或 Compose。保留已有 TOTP 测试数据时必须继续使用同一密钥；本地数据库重建后可以重新生成。

## 运行入口

同时启动后端和 PC 前端开发服务：

```bash
mise run dev
```

`dev` 通过两个内部任务并行启动服务。运行前安装前端依赖，并确保 PostgreSQL 和对象存储可用；使用本地 Compose 时先执行 `mise run infra:up`。该命令长期占用端口，仅由开发者按需执行，自动化代理不主动启动。

Spring Boot 主应用：

```bash
mise exec -- mvn -f server/pom.xml spring-boot:run
```

PC 前端开发服务：

```bash
mise exec -- pnpm --dir frontend run dev:web
```

`mise exec -- pnpm --dir frontend run dev:web` 会长期占用端口，只由开发者在需要预览时本地执行；自动化代理不主动启动。

默认端口和运行参数分别以 `application.yaml` 和 Vite+ 配置为准，本文不复制运行参数表。

## 按范围验证

结构优化或跨前后端改动使用统一验证入口：

```bash
mise run verify
```

该任务执行根目录结构与严格验证脚本的回归测试、全部源码行数检查、Docker 可用性检查、后端 Spotless 和 Maven 测试，以及前端 `ready`。后端使用 clean 清除旧报告，随后核对全部 Surefire 报告：没有报告、未执行的容器集成测试类、零用例、失败、错误或跳过均阻断完整验证。前端测试脚本在未发现测试时失败。局部 Maven 测试仍可在没有 Docker 时跳过容器测试，但该结果不能作为完整验收。

前端 `ready` 自身包含共享与页面依赖边界检查、前端源码行数检查、类型与 lint、测试和构建。结构脚本使用已配置的 Node.js，无需安装 Perl。

[GitHub Actions](../.github/workflows/verify.yml) 在拉取请求、main 推送及手动触发时调用同一 `mise run verify`，安装锁定前端依赖并保存后端测试文本摘要。Action 使用固定提交版本、只读仓库权限，不包含部署步骤；报告不上传包含运行日志的 XML。远端执行结果及仓库分支保护需在 GitHub 中独立核实。

| 改动范围 | 真实入口 |
| --- | --- |
| 全部前端包 | `mise exec -- pnpm --dir frontend run check`、`mise exec -- pnpm --dir frontend run test`；影响构建时运行 `mise exec -- pnpm --dir frontend run build` |
| 单个前端包 | `mise exec -- pnpm --dir frontend --filter @archive-management/web run check`、`mise exec -- pnpm --dir frontend --filter @archive-management/web run test` ；共享包将 `--filter` 的值替换为 `@archive-management/frontend-core` |
| 后端 Java | `mise exec -- mvn -f server/pom.xml spotless:check`、`mise exec -- mvn -f server/pom.xml compile`、相关 `mise exec -- mvn -f server/pom.xml test` |
| 后端发布包 | `mise exec -- mvn -f server/pom.xml package` |
| 源码职责规模 | `mise exec -- node scripts/source-lines.mjs`；只查看提示时追加 `--report` |
| 前端依赖边界 | `mise exec -- pnpm --dir frontend run check:structure` |

后端需要直接运行 Maven 时，先 `cd server` 再执行 Maven 命令。前端需要直接运行 pnpm 或 Vite+ 时先 `cd frontend`，再使用项目依赖提供的 `pnpm ...` 或 `pnpm exec vp ...`；可用子命令以 `pnpm exec vp help` 为准。

## 保留的 mise 任务

| 任务 | 用途 |
| --- | --- |
| `mise run install` | 并行安装前端依赖和下载后端 Maven 依赖 |
| `mise run verify` | 顺序检查项目结构、后端格式与测试、全部前端包 |
| `mise run dev` | 并行启动后端和 PC 前端开发服务 |
| `mise run infra:up` | 使用固定 Compose 文件启动或恢复容器，等待健康且不重建 |
| `mise run infra:stop` | 停止容器，保留容器及数据 |
| `mise run infra:down` | 停止并删除容器及 Compose 网络，不附加 `--volumes` |
| `mise run frontend:deprecated:check` | 依次检查共享包和 PC 前端的过时 API |
| `mise run server:deprecated:check` | 使用固定 OpenRewrite 配方检查过时 API 并导出结果 |

后端格式化、依赖下载和 OpenRewrite 迁移可在 `server/` 直接运行 `mise exec -- mvn spotless:apply`、`mise exec -- mvn dependency:resolve`、`mise exec -- mvn rewrite:dryRun` 或 `mise exec -- mvn rewrite:run`。前端自动修复可在 `frontend/` 运行 `mise exec -- pnpm run check:fix`。

## 工具链排障

环境或包管理行为异常时保留以下输出：

```bash
mise doctor
cd frontend
pnpm --version
pnpm exec vp --version
```

后端启动提示 Spring Session、Quartz 或 Flowable 表缺失时，先检查 Flyway 是否启用及结构迁移位置；测试依赖数据库失败时，确认 Docker/Testcontainers 或外部 PostgreSQL 环境可用。运行期配置项以 `application.yaml` 为准。
