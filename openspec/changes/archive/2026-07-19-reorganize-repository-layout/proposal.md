## Why

仓库当前将后端应用、前端应用、共享包、治理脚本和构建产物入口平铺在根目录，目录层级不能直接表达职责与部署边界。随着 Java 主应用、Go 预览服务和前端共享包并存，继续使用实现语言或历史路径作为主要分类会增加导航、构建和维护成本，因此需要按后端、前端及工程支撑职责统一整理。

## What Changes

- **BREAKING**：将 Spring Boot 主应用从 `server/` 迁移到 `backend/archive-server/`。
- **BREAKING**：将 Go 文件预览服务从 `preview/` 迁移到 `backend/preview-service/`。
- **BREAKING**：将 PC 管理前端从 `web/` 迁移到 `frontend/admin/`。
- **BREAKING**：将前端共享核心从 `frontend-core/` 迁移到 `frontend/packages/core/`。
- 将仓库治理脚本和 PNPM 补丁归入 `tooling/`，保留各应用自己的语言标准目录和构建边界。
- 将容器构建定义归属到对应应用，部署侧只保留环境与反向代理配置。
- 更新 Task、PNPM、Vite、Maven、Go、Docker、忽略规则、治理检查及当前文档中的所有有效路径。
- 不改变业务行为、HTTP API、数据库结构、Java 包名、前端包名或运行时部署拓扑。
- 非目标：不创建尚无真实内容的移动端、Rust 服务、公共门户、跨语言合同目录或 Kubernetes 目录；不重写历史 OpenSpec 归档和 `docs/superpowers/` 历史材料。

## Capabilities

### New Capabilities

无。仓库目录属于工程治理，由设计、任务和稳定架构文档约束，不新增业务能力规格。

### Modified Capabilities

- `file-preview-service`: 将文件预览服务的规范路径和根任务合同从 `preview/` 调整为 `backend/preview-service/`。

## Impact

- 受影响代码和配置：`Taskfile.yml`、PNPM workspace、Vite 配置、Docker 构建、Git 忽略规则、治理脚本和测试设置。
- 受影响真相源：`AGENTS.md`、`README.md`、`CONTRIBUTING.md`、`docs/architecture.md`、`docs/development.md`、`docs/deployment.md`、`openspec/specs/file-preview-service/spec.md`。
- 受影响路径：`server/`、`preview/`、`web/`、`frontend-core/`、`scripts/`、`patches/`、根级前端测试设置与根级 Dockerfile。
- 外部接口、数据库和运行时端口不受影响；开发者和 CI 使用的文件路径属于破坏性工程变更。
