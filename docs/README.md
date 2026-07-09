# 文档总览

本文档目录用于承载项目交付、开发、部署、运维和使用说明。业务合同仍以 `openspec/specs/` 为准；行业知识和背景材料仍放在 `docs/archive-knowledge/`。

## 入口文档

- `../README.md`：项目简介、主要目录和常用命令入口。
- `../PRODUCT.md`：产品定位、用户类型、产品边界和前端设计原则。
- `../DESIGN.md`：PC 管理界面的设计系统约束。
- `../AGENTS.md`：仓库协作规则、后端架构约定、API 设计约定和检查清单。
- `../openspec/README.md`：OpenSpec 规格和 change 状态索引。

## 工程文档

- `development.md`：本地开发环境、依赖安装、启动和验证命令。
- `architecture.md`：系统边界、后端分层、前端结构、持久化和运行时基础设施。
- `database.md`：PostgreSQL、Flyway、项目表、第三方表、动态表和本地重置策略。
- `api.md`：HTTP API 使用约定、认证、分页、错误响应和资源索引。
- `security.md`：认证、会话、权限、数据范围、请求签名、CORS 和审计。
- `deployment.md`：部署组件、配置覆盖、数据库迁移、存储和发布步骤。
- `ops-runbook.md`：健康检查、日志、常见故障、备份恢复和应急处理。

## 使用文档

- `user-guide/README.md`：PC 端页面地图和常见业务流程。
- `archive-knowledge/README.md`：档案行业规范知识库索引。

## 维护规则

- 文档中涉及接口字段、状态机、权限边界或验收场景时，先更新对应 OpenSpec，再更新说明文档。
- 文档中涉及命令、端口、环境变量或配置项时，以 `Taskfile.yml`、`package.json`、`server/src/main/resources/application.yaml` 和实际源码为准。
- 不把未实现能力写成已交付能力；未接入的功能写成边界、限制或规划说明。
- 不在文档里提交密钥、口令、连接串中的真实密码或客户环境信息。
