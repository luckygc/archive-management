## Why

当前档案能力已经有分类动态表、item/volume、明细行、数据范围和检索基础，但还缺少能同时服务档案馆、政府、企业、高校和业务系统归档的统一治理底座。需要先把治理方案、本体核心和本地规则引擎收成正式 OpenSpec 合同，避免后续分类、著录、档号、保管、移交和四性检测各自生长。

## What Changes

- 新增档案治理方案能力，用于管理某个机构或客户采用的本体、规则、分类、著录、档号和利用控制配置版本。
- 新增档案本体核心能力，用于统一定义对象类型、属性类型、关系类型和事件类型，并约束它们如何映射到现有 item/volume、动态字段、明细行、文件组件和过程对象。
- 新增本地规则引擎能力，用受控条件树、事实解析、操作符白名单、effect 和决策追踪表达本地差异。
- 明确第一版覆盖全域最小闭环：治理方案、本体核心、规则定义和规则校验链路必须可运行；行业模板和完整分类、著录、档号、移交细则后续按独立 change 扩展。
- 不把档案主数据改成 EAV、三元组表或自由 JSON；现有 `ArchiveItem`、`ArchiveVolume`、分类动态表、明细行和全文投影边界保持不变。
- 不引入自由脚本规则引擎；规则条件和 effect 都必须是结构化、可校验、可追踪的合同。

## Capabilities

### New Capabilities

- `archive-governance-scheme`: 定义治理方案、方案版本、状态流转、适用范围、发布冻结和历史解释边界。
- `archive-ontology-core`: 定义档案对象类型、属性类型、关系类型、事件类型，以及它们与现有档案数据模型的映射边界。
- `archive-local-rule-engine`: 定义本地规则类型、触发点、作用域、条件树、事实解析、effect、决策追踪和规则安全边界。

### Modified Capabilities

- 无。

## Impact

- 后端将新增 `module/archive/governance`、`module/archive/ontology` 和 `module/archive/rule` 相关领域对象、Repository、Mapper、Service 和 Controller。
- 数据库将新增治理方案、本体定义、规则定义、规则绑定和规则执行追踪相关表。
- 前端将新增治理方案、本体属性和本地规则的管理入口；现有分类、字段、档案列表和搜索页面只通过后续 change 接入这些配置。
- 现有 `archive-metadata`、`archive-record-search`、数据范围和权限合同不在本 change 中改变。
- 不新增外部运行时依赖；规则引擎第一版为本地受控解释器。
