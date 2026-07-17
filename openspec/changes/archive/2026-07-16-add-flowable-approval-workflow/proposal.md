## Why

当前系统缺少统一审批流能力，后续档案移交、归档、销毁、借阅等业务如果各自实现审批状态机会重复处理待办、候选人、历史审计和流程版本问题。引入 Flowable 作为嵌入式 BPMN 引擎，可以把通用流程运行时交给成熟组件，项目自身只维护档案业务语义和 API 合同。

## What Changes

- 引入 Flowable BPMN process engine，用于审批流定义部署、流程实例启动、人机任务办理和历史查询。
- 新增审批流业务模块，对外暴露项目自有审批定义、实例、待办、办理和历史 API，不让业务模块直接依赖 Flowable API。
- 新增 Flowable 基础设施适配层，集中封装流程启动、任务查询、任务完成、流程终止和变量转换。
- 新增首版顺序审批模型：支持按流程定义发起审批、查询我的待办、同意、驳回、撤回或终止，并记录审批意见。
- 新增流程定义版本冻结规则：运行中实例按启动时定义版本执行，后续定义调整不影响既有实例。
- 暂不引入 Flowable UI、Modeler、IDM、CMMN、DMN 和 Spring Data JPA；前端流程配置首版由项目自有页面和后端转换生成 BPMN。

## Capabilities

### New Capabilities

- `approval-workflow`: 审批流定义、实例、待办、办理动作、历史记录和业务接入合同。

### Modified Capabilities

- 无。

## Impact

- 后端：新增 Flowable 依赖、基础设施适配、审批流业务模块、审批 API、领域事件和架构边界测试。
- 数据库：新增项目自有审批扩展表；Flowable 引擎表由 Flowable 自身维护，不改名为项目表。
- 前端：新增审批流定义管理、审批中心待办、我发起的、已办和流程详情页面。
- API：新增 `/api/v1/approval-workflow-definitions`、`/api/v1/approval-workflow-instances`、`/api/v1/approval-workflow-tasks` 等资源导向接口，自定义动作使用 AIP 冒号动作。
- 依赖：引入 `org.flowable:flowable-spring-boot-starter-process`，不引入完整 Flowable 应用套件。
