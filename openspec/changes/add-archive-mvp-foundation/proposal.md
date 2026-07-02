## Why

`docs/archive-knowledge/archive-system-capability-map.md` 已经区分 POC、MVP、基础能力和完整能力，但当前 OpenSpec 只覆盖元数据、搜索、文件存储、登录和 intake 等局部能力。项目需要一份 MVP 完成门槛，避免把已完成的 POC/核心基础能力误判为可持续使用的 MVP。

## What Changes

- 新增档案系统 MVP 基础合同，定义 MVP 完成前必须具备的业务能力和验收边界。
- 明确当前已有能力只覆盖分类、字段、item/volume、搜索和文件存储基础，不等于 MVP 完成。
- 将全宗基础治理、手工档号唯一校验、基础导入导出、基础审计、数据范围、档案电子文件上传、列表、预览、下载、删除列为 MVP 必做能力。
- 明确分类方案版本、档号规则引擎、完整归档流程、移交接收、四性检测、利用审批和长期保存能力不属于本 MVP change。

## Capabilities

### New Capabilities

- `archive-mvp-foundation`: 档案系统 MVP 完成门槛、必备能力和后续实施切片合同。

### Modified Capabilities

- `archive-metadata`: 不修改现有需求；本 change 只引用其作为 MVP 已有基础。
- `archive-record-search`: 不修改现有需求；本 change 只引用其作为 MVP 已有基础。
- `file-storage`: 不修改现有需求；本 change 只引用其作为 MVP 已有基础。

## Impact

- 文档：新增 MVP 差距清单，更新档案知识库入口。
- OpenSpec：新增 `archive-mvp-foundation` 能力规格和后续实施任务。
- 后续代码影响：archive metadata、archive item、storage、authorization 和前端档案库工作区会在独立 change 中继续实现。
