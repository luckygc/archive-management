# Archive Management

面向机构内部使用的档案管理系统。PC 工作台使用 Vue 3、TypeScript 和 Element Plus，后端为单 Spring Boot 应用，并配套独立文件预览服务。

## 当前真相源

| 主题 | 入口 |
| --- | --- |
| 产品定位与原则 | [`PRODUCT.md`](PRODUCT.md) |
| Element Plus 设计系统 | [`DESIGN.md`](DESIGN.md) |
| 仓库协作规则 | [`AGENTS.md`](AGENTS.md) |
| 稳定技术边界 | [`docs/architecture.md`](docs/architecture.md) |
| 开发、部署、API、安全与运维 | [`docs/README.md`](docs/README.md) |
| 通用 API 合同 | [`openspec/specs/api-contract/spec.md`](openspec/specs/api-contract/spec.md) |
| 业务合同与 change | [`openspec/README.md`](openspec/README.md) |

业务字段、状态机、权限边界和验收场景以对应 OpenSpec 为准；命令和运行配置分别以 [`Taskfile.yml`](Taskfile.yml)、构建配置和 [`server/src/main/resources/application.yaml`](server/src/main/resources/application.yaml) 为准。

## 顶层目录

| 路径 | 职责 |
| --- | --- |
| `web/` | PC 管理界面 |
| `frontend-core/` | 框架无关的前端共享基础能力 |
| `server/` | Spring Boot 后端主应用 |
| `preview/` | 独立文件预览服务 |
| `openspec/` | API 与业务合同 |
| `docs/` | 开发、架构、部署、运维和使用说明 |

## 常用入口

```bash
task --list
task frontend-install
task governance-check
```

本地准备、运行和按范围验证详见 [`docs/development.md`](docs/development.md)。`task web-dev` 会长期占用端口，仅由开发者在需要预览时本地启动。

当前仓库未声明开源许可证；对外开源或分发前须由项目所有者明确许可证和版权声明。
