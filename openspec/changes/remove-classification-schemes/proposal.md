## Why

当前分类方案只在全局分类树上增加分组和默认回退，而全宗实际已经直接维护可用分类范围，分类字段、动态表和规则也都直接归属于分类。保留方案层会增加数据库、API 和页面概念，却不能提供独立的模板复制、版本或批量采用价值。

## Goals

- 删除分类方案资源和默认分类方案，使档案分类形成一棵全局分类树。
- 由每个全宗显式勾选可用分类；未配置任何分类时，该全宗没有可用分类且不能新建档案或案卷。
- 删除全宗默认分类标记；建档时由用户在该全宗已勾选分类中明确选择。
- 服务端在档案和案卷写入边界校验全宗与分类的显式关联，前端选择范围不能替代服务端校验。

## Non-Goals

- 不改变分类字段、动态表、明细表、布局、唯一规则和运行时规则的归属方式。
- 不把全宗绑定到分类模板，不引入分类方案的替代分组、标签、版本或继承模型。
- 不因取消全宗分类勾选而删除或隐藏已经存在的历史档案。
- 不改变档案数据范围和功能权限模型。

## What Changes

- **BREAKING** 删除分类方案数据库表、实体、Repository、Service、DTO 和 `/api/v1/archive-classification-schemes` API。
- **BREAKING** 从档案分类数据库、API 和前端类型中删除 `schemeId`，分类父子关系只校验存在性和无循环。
- **BREAKING** 从全宗可用分类关系、API 和前端类型中删除 `defaultFlag`，系统不再维护默认分类。
- 档案分类页面移除方案选择器与方案标签，直接展示全局分类树。
- 全宗可用分类继续逐项保存；空集合表示没有可用分类，不再回退默认方案或全部分类。
- 档案条目、案卷和导入写入在副作用前校验目标分类已显式关联到目标全宗。
- 更新领域词汇、用户手册、数据库说明和稳定规格，移除默认分类方案及方案复用表述。

## Capabilities

### New Capabilities

无。

### Modified Capabilities

- `archive-classification-scheme`: 删除分类方案管理和默认方案解析，改为全局分类树与全宗显式可用分类范围合同。
- `archive-record-routing`: 创建或修改档案条目和案卷时校验全宗已显式选择目标分类。
- `archive-import-export`: 导入预检和提交校验全宗已显式选择目标分类。

## Impact

- 真相源：`CONTEXT.md`、`openspec/specs/archive-classification-scheme/spec.md`、`archive-record-routing`、`archive-import-export`、`docs/database.md` 和 `docs/user-guide/README.md`。
- 数据库：直接调整尚未发布的基础 Flyway 和示例数据，删除 `am_archive_classification_scheme` 与 `am_archive_category.scheme_id`。
- 后端：档案元数据实体、Repository、Service、Controller、请求响应类型及条目、案卷、导入写入校验。
- 前端：分类类型和 API client、分类树页面、全宗可用分类弹窗及相关测试。
- 外部调用方：分类创建和查询不再传递或读取 `schemeId`；分类方案 API 被删除。
