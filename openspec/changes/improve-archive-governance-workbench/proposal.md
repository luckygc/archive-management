## Why

当前档案治理页面只展示治理方案和版本列表，用户无法看到一个治理版本到底适用于哪些全宗或分类，也无法维护它绑定了哪些本体、规则和后续配置引用。这会让“治理方案作为装配根”的模型在管理端看起来像空壳。

需要把已有治理范围、治理绑定和默认版本解析能力补成可读、可维护、可试算的工作台，使档案管理员能判断某个治理版本是否已经具备发布和使用条件。

## What Changes

- 为治理方案版本新增适用范围和装配绑定的读取 API，返回已保存的范围与绑定列表。
- 改造治理方案页面为治理版本工作台：选中版本后展示版本概览、适用范围、装配绑定和默认解析试算。
- 前端 API 类型从 `unknown` 改为明确的治理范围、治理绑定响应类型，并在保存后刷新当前版本配置。
- 保持治理绑定仍是配置引用，不在治理方案版本中保存档案实例值。

## Capabilities

### New Capabilities

- `archive-governance-workbench`: 定义管理端读取、维护和试算治理方案版本装配内容的能力。

### Modified Capabilities

无。

## Impact

- 后端：`module/archive/governance` 的 Service 与 Controller 新增读取范围、读取绑定接口。
- 前端：`web/src/pages/archive-governance/ArchiveGovernancePage.tsx`、`web/src/shared/api/archive.ts`、`web/src/shared/types/archive.ts`。
- 测试：治理服务测试、前端页面测试补充治理范围、治理绑定和默认解析工作台覆盖。
