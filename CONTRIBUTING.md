# 贡献指南

本文说明普通贡献流程。仓库协作、架构强边界和技能路由以 [`AGENTS.md`](AGENTS.md) 为准；本文不复制该手册。

## 开始前

- 拉取远程变更后运行 `task frontend-install`。
- 阅读 `AGENTS.md`、相关当前文档、对应 OpenSpec 和已有实现。
- 先确认改动 owner；业务字段、状态机、权限和验收场景进入对应 OpenSpec，通用 API 合同进入 `openspec/specs/api-contract/spec.md`。
- 不提交密钥、密码、客户数据、构建产物或本地私有配置。

## 开发流程

1. 按最小闭环实现当前需求，不顺手扩展相邻模块。
2. 同步更新受影响的合同或说明文档。
3. 运行 `task governance-check`。
4. 运行与改动范围匹配的构建和测试任务。
5. 运行 `git diff --check`，确认工作树只包含预期改动。

## 范围验证

以下命令均来自 [`Taskfile.yml`](Taskfile.yml)；按实际范围选择，不能用较窄检查代替受影响模块的验证。

| 改动范围 | 至少执行 |
| --- | --- |
| 当前规范、OpenSpec 或工程文档 | `task governance-check` |
| 前端 | `task frontend-check`、`task frontend-test`；影响构建或发布时再运行 `task frontend-build` |
| 后端 Java | `task server-format-check`、`task server-compile` 和相关 `task server-test` |
| 文件预览服务 | `task preview-test`、`task preview-build` |

后端 Maven 项目根目录是 `backend/archive-server/`；需要直接运行 Maven 时先进入该目录。前端使用项目依赖提供的 Vite+，直接调用时使用 `pnpm exec vp ...`。完整本地开发入口见 [`docs/development.md`](docs/development.md)。

## 文档 owner

- 产品定位维护在 `PRODUCT.md`，Element Plus 设计系统维护在 `DESIGN.md`。
- 稳定技术边界维护在 `docs/architecture.md`；开发、部署、API、安全和运维说明维护在对应 `docs/` 文档。
- 业务合同和验收场景维护在 `openspec/specs/` 或活动 change；发生冲突时先校准合同 owner。
- `docs/superpowers/**` 是历史资料，不作为当前规范。

## 许可证

当前仓库未声明开源许可证。对外开源、分发或允许第三方使用前，必须由项目所有者明确许可证和版权声明。
