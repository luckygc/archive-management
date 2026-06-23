## ADDED Requirements

### Requirement: 档案记录精确筛选
系统 SHALL 支持按分类动态字段进行精确筛选。

#### Scenario: 使用允许精确筛选的字段过滤
- **WHEN** 客户端提交包含 `exactFilters` 的档案记录查询请求
- **THEN** 系统 SHALL 只接受字段定义中 `exact_searchable=true` 的字段编码
- **AND** 系统 SHALL 使用字段定义解析出的动态列名构造查询条件
- **AND** 系统 SHALL 使用参数绑定传递筛选值

#### Scenario: 使用不允许精确筛选的字段过滤
- **WHEN** 客户端提交的 `exactFilters` 包含未启用精确筛选的字段
- **THEN** 系统 SHALL 拒绝查询
- **AND** 响应 SHALL 说明该字段不允许作为筛选条件

#### Scenario: 按全宗固定字段过滤
- **WHEN** 客户端提交全宗编码筛选条件
- **THEN** 系统 SHALL 通过分类动态表固定字段 `fonds_code` 过滤记录
- **AND** 系统 SHALL NOT 要求 `fonds_code` 在字段定义表中存在

### Requirement: 档案记录全文投影
系统 SHALL 为全文检索维护独立投影表，并以所有启用动态字段生成投影文本。

#### Scenario: 创建档案记录后维护投影
- **WHEN** 客户端创建档案记录且分类存在启用的动态字段
- **THEN** 系统 SHALL 将所有启用动态字段的字段名称和值拼接为 `search_text`
- **AND** 全文投影表 SHALL 只保存记录 ID、`search_text`、索引版本和投影维护时间

#### Scenario: 删除档案记录后删除投影
- **WHEN** 客户端删除档案记录
- **THEN** 系统 SHALL 删除该记录对应的全文投影行
- **AND** 系统 SHALL NOT 依赖全文投影表保存删除状态

#### Scenario: 动态字段定义变更
- **WHEN** 客户端新增、启用或重命名动态字段定义
- **THEN** 系统 SHALL NOT 阻塞字段定义保存来同步重建历史投影
- **AND** 系统 SHALL 允许通过单独重建流程补齐历史投影

### Requirement: pg_textsearch 关键词检索
系统 SHALL 使用 PostgreSQL `pg_textsearch` 扩展和 BM25 索引执行关键词检索。

#### Scenario: 使用关键词查询档案记录
- **WHEN** 客户端提交 `keyword`
- **THEN** 系统 SHALL 先查询全文投影表获得匹配档案记录 ID 和相关性分数
- **AND** 系统 SHALL 再回连统一档案记录主表和分类动态表过滤分类、全宗、删除状态并返回记录详情
- **AND** 系统 SHALL 优先按全文相关性分数排序

#### Scenario: 未提交关键词查询档案记录
- **WHEN** 客户端未提交 `keyword`
- **THEN** 系统 SHALL 直接按主表和分类动态表执行常规动态查询
- **AND** 系统 SHALL NOT 访问全文投影表作为必要步骤
