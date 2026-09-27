# Archive Management

面向机构内部使用的档案管理系统。PC 工作台使用 Vue 3、TypeScript 和 Element Plus，后端为单 Spring Boot 应用。

## 当前真相源

| 主题 | 入口 |
| --- | --- |
| 项目目标、产品定位与工程约定 | [`SPEC.md`](SPEC.md) |
| 领域词汇与概念关系 | [`docs/domain-glossary.md`](docs/domain-glossary.md) |
| Element Plus 设计系统 | [`docs/design-system.md`](docs/design-system.md) |
| 仓库协作规则 | [`AGENTS.md`](AGENTS.md) |
| 稳定技术边界 | [`docs/architecture.md`](docs/architecture.md) |
| 开发、部署、API、安全与运维 | [`docs/README.md`](docs/README.md) |
| 通用 API 合同 | [`specs/SPEC-项目API合同.md`](specs/SPEC-项目API合同.md) |
| 稳定能力规格与进行中变更 | [`specs/README.md`](specs/README.md)、[`tasks/README.md`](tasks/README.md) |

业务字段、状态机、权限边界和验收场景以对应能力规格及进行中变更为准；命令和运行配置分别以 [`mise.toml`](mise.toml)、构建配置和 [`application.yaml`](backend/archive-server/src/main/resources/application.yaml) 为准。

## 顶层目录

| 路径 | 职责 |
| --- | --- |
| `backend/archive-server/` | Spring Boot 后端主应用 |
| `frontend/` | pnpm/Vite+ 前端工作区配置、测试入口与前端项目 |
| `frontend/admin/` | PC 管理界面 |
| `frontend/packages/core/` | 框架无关的前端共享基础能力 |
| `deploy/` | 本地 Compose、反向代理和部署配置 |
| `specs/` | 当前稳定的业务与 API 验收规格 |
| `tasks/` | 进行中变更的规格、计划与任务 |
| `docs/` | 开发、架构、部署、运维和使用说明 |

## 常用入口

```bash
mise tasks ls
mise run frontend-install
mise run governance-check
```

本地准备、运行和按范围验证详见 [`docs/development.md`](docs/development.md)。`mise run web-dev` 会长期占用端口，仅由开发者在需要预览时本地启动。

当前仓库未声明开源许可证；对外开源或分发前须由项目所有者明确许可证和版权声明。
