## Why

管理端目前直接依赖 Element Plus `el-table`，表格能力分散在各业务页面，且档案检索虽然已支持 `orderBy` 数组，界面仍只能提交单列排序。需要统一表格交互和状态管理，使多列排序可发现、可测试，并避免在服务端分页列表中只排序当前页。

## What Changes

- 引入稳定版 TanStack Table v9，提供统一的管理端数据表格组件和列定义类型。
- **BREAKING**：移除管理端页面对 Element Plus `el-table`、`el-table-column` 的直接使用，表格样式与行为改由项目共享组件维护。
- 本地完整集合支持按列排序和通过 Shift/Ctrl/Command 追加多列排序，并显示方向与排序优先级。
- 档案检索表把完整多列排序顺序映射为既有 `orderBy` 请求；服务端分页但未提供排序合同的列表不执行仅当前页排序。
- 保留加载、空状态、固定操作列、响应式横向滚动、树形行和当前行选择等现有必要行为。
- 增加共享组件测试和关键业务表格的多列远程排序测试。

### 目标

- 管理端所有业务数据表格使用同一套 TanStack Table v9 状态和渲染基础设施。
- 用户能够识别并控制多列排序，排序结果与分页边界保持一致。
- 迁移后不降低键盘可用性、窄屏可读性、加载和空状态反馈。

### 非目标

- 不为尚未声明 `orderBy` 的业务接口新增通用排序参数或改造后端 SQL。
- 不替换 Element Plus 的表单、弹窗、按钮、标签等非表格组件。
- 不在本次变更中引入虚拟滚动、列拖拽、行内编辑或新的分页模式。

## Capabilities

### New Capabilities

- `admin-data-table`: 统一管理端数据表格的渲染、可访问性、本地与远程多列排序、加载、空状态、树形行和响应式行为。

### Modified Capabilities

- `archive-record-search`: 管理端档案检索结果表由单列远程排序改为提交有优先级的多列 `orderBy`。

## Impact

- 受影响真相源：新增 `admin-data-table` 规格，并增量修改 `archive-record-search`；设计仍遵循 `PRODUCT.md`、`DESIGN.md`。
- 受影响代码：`frontend/admin/src` 下共享组件、所有直接使用 `el-table` 的业务页面及其测试。
- 依赖变化：`frontend/admin` 新增 MIT 协议的 `@tanstack/vue-table` v9；Element Plus 保留用于其他界面组件。
- API 与后端：复用现有档案检索 `orderBy` 数组合同，不新增接口、数据库迁移或后端依赖。
