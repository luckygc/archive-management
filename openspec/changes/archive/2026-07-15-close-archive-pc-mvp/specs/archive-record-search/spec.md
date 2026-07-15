## MODIFIED Requirements

### Requirement: 档案条目高级查询

系统 SHALL 使用统一高级查询条件树表达后台管理筛选和普通用户结构化筛选。

#### Scenario: 使用字段操作符过滤

- **WHEN** 客户端提交包含 `where.conditions` 的档案条目查询请求
- **THEN** 系统 SHALL 只接受字段定义中 `exact_searchable=true` 或唯一约束索引覆盖的字段编码
- **AND** 系统 SHALL 使用字段定义解析出的动态列名构造查询条件
- **AND** 系统 SHALL 使用参数绑定传递筛选值
- **AND** 系统 SHALL 对文本输入执行 `trimToNull`
- **AND** 系统 SHALL 使用大写枚举值表达操作符，例如 `EQ`、`CONTAINS`、`STARTS_WITH`、`GTE`、`LTE`、`BETWEEN`、`IS_EMPTY`、`IS_NOT_EMPTY`
- **AND** 系统 SHALL 按字段类型限制可用操作符和前端控件
- **AND** `where.conditions` SHALL 固定按 `AND` 组合
- **AND** 请求体 SHALL NOT 提供 `logic` 字段切换条件组合方式

#### Scenario: 使用不允许精确筛选的字段过滤

- **WHEN** 客户端提交的 `where.conditions` 包含未启用精确筛选且未被唯一约束索引覆盖的字段
- **THEN** 系统 SHALL 拒绝查询
- **AND** 响应 SHALL 说明该字段不允许作为筛选条件

#### Scenario: 按固定字段或可搜索动态字段排序

- **WHEN** 客户端提交 `orderBy`
- **THEN** `orderBy.field` SHALL 支持固定排序字段编码或可搜索动态字段编码
- **AND** cursor 搜索接口 SHALL 在 JSON 请求体中提交 `orderBy`
- **AND** 可搜索动态字段 SHALL 指字段定义中 `exact_searchable=true` 或唯一约束索引覆盖的字段
- **AND** 系统 SHALL 使用字段元数据将动态字段编码映射为当前分类动态表列名
- **AND** 系统 SHALL 拒绝不可搜索动态字段排序

#### Scenario: 使用关联分类分组过滤

- **WHEN** 客户端提交 `relatedGroups`
- **THEN** 每个关联分组 SHALL 使用系统按当前分类派生出的关联档案分类
- **AND** 系统 SHALL 将该分组编译为当前条目查询上的结构化 `exists` 条件
- **AND** 关联分组 SHALL 使用关联分类自己的字段元数据、操作符和控件规则
- **AND** 关联方向 SHALL 由关联分类派生结果默认带出，不作为用户筛选控件
- **AND** 关联方向 SHALL 使用大写枚举值 `OUTGOING` 或 `BOTH`
- **AND** 多个关联分组 SHALL 默认按 `AND` 组合

#### Scenario: 获取可用于关联筛选的分类

- **WHEN** 客户端请求当前档案分类的关联筛选分类
- **THEN** 系统 SHALL 返回当前分类作为来源分类时已关联出去的目标分类
- **AND** 若同一分类对同时存在反向关联，系统 SHALL 返回方向 `BOTH`
- **AND** 系统 SHALL NOT 返回仅关联到当前分类的 `INCOMING` 分类作为默认高级筛选分组

#### Scenario: 按全宗固定字段过滤

- **WHEN** 客户端提交全宗编码筛选条件
- **THEN** 系统 SHALL 通过 `am_archive_item` 固定字段 `fonds_code` 过滤记录
- **AND** 系统 SHALL NOT 要求 `fonds_code` 在字段定义表中存在

#### Scenario: 按所属案卷固定字段过滤

- **WHEN** 客户端在 `SearchArchiveItemsRequest` 中提交可空 `volumeId`
- **THEN** 系统 SHALL 通过 `am_archive_item.volume_id` 过滤指定案卷内的未删除档案
- **AND** `volumeId` SHALL 作为业务筛选字段进入 JSON 请求体和 cursor 查询摘要
- **AND** 带 cursor 的后续请求 SHALL 重复提交与首次查询相同的 `volumeId`
- **AND** URL query 中的 `limit`、`cursor` 和 `requestTotal` SHALL 继续作为分页控制字段
- **AND** `limit`、`cursor` 和 `requestTotal` SHALL NOT 进入 cursor 查询摘要
