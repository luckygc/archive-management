# 文档总览

本文档目录承载开发、架构、部署、运维、API 使用和用户说明。业务合同以 `openspec/specs/` 为准；行业知识和背景材料放在 `docs/archive-knowledge/`。

## 入口文档

- `../README.md`：项目简介、当前真相源和常用入口。
- `../PRODUCT.md`：产品定位、用户、产品表面、性格和产品原则。
- `../DESIGN.md`：Element Plus 当前设计系统。
- `../AGENTS.md`：仓库协作规则、强边界和技能路由。
- `../openspec/README.md`：OpenSpec 规格和 change 状态索引。
- [Superpowers 历史资料声明](superpowers/README.md)：历史资料范围与当前真相源指引。

## 工程文档

- `development.md`：本地开发环境、依赖安装、启动和验证命令。
- `architecture.md`：系统边界、后端分层、前端结构、持久化和运行时基础设施。
- `database.md`：PostgreSQL、Flyway、项目表、第三方表、动态表和本地重置策略。
- `api.md`：HTTP API 使用入口、通用合同和业务规格索引。
- `security.md`：认证、会话、权限、数据范围、请求签名、CORS 和审计。
- `deployment.md`：部署组件、配置覆盖、数据库迁移、存储和发布步骤。
- `ops-runbook.md`：健康检查、日志、常见故障、备份恢复和应急处理。

## 使用文档

- `user-guide/README.md`：PC 端页面地图和常见业务流程。
- `archive-knowledge/README.md`：档案行业规范知识库索引。

## 维护规则

- 文档中涉及接口字段、状态机、权限边界或验收场景时，先更新对应 OpenSpec，再更新说明文档。
- 文档中涉及命令、端口、环境变量或配置项时，以 `Taskfile.yml`、`package.json`、`server/src/main/resources/application.yaml` 和实际源码为准。
- 当前规范或 OpenSpec 变更必须运行 `task governance-check`；其他验证按改动范围从 `Taskfile.yml` 选择。
- 不把未实现能力写成已交付能力；未接入的功能写成边界、限制或规划说明。
- 不在文档里提交密钥、口令、连接串中的真实密码或客户环境信息。
