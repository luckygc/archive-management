## Why

项目的业务复杂度正在集中到少数巨型 Service、前端 API 和类型文件中，同时存在 Spring Bean 公开方法自调用、总门面与专项 Service 双入口、重复审计赋值和跨业务域前端文件等维护负担。现在需要用可执行的架构约束和分批重构建立清晰边界，避免后续功能继续放大这些问题。

## What Changes

- 新增架构约束，禁止同一 Spring Bean 的 public 方法调用本类其他 public 方法。
- 新增业务类型约束，禁止为只有一个实现且没有稳定端口价值的 Service 或 Manager 保留接口。
- 收缩元数据和档案记录巨型 Service，删除只做转发的总门面和重复公开入口。
- 验证 Hibernate 无状态 Repository 审计拦截器后，删除固定实体写入路径的重复手工用户审计赋值。
- 按业务域拆分前端 API 与类型文件，删除已有公共实现的局部重复。
- 保持现有 HTTP API、数据库结构、权限、错误和前端业务行为不变。

## Capabilities

### New Capabilities

- `code-maintainability`: 规定业务类抽象、Spring Bean 方法调用、模块入口、审计来源和前端代码组织的可执行维护性约束。

### Modified Capabilities

无。此次变更不修改既有业务规格和外部 API 合同。

## Impact

- 后端：`ArchitectureRulesTest`、模块 Service/Manager、相关 Controller 与测试、Hibernate 审计写入路径。
- 前端：`web/src/shared/api`、`web/src/shared/types`、相关页面导入和重复错误处理代码。
- 文档：仓库协作规则、架构文档和分阶段实施计划。
- 不新增运行时依赖，不修改数据库迁移，不改变项目自有 HTTP API。
