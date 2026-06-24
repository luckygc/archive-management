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
- **THEN** 系统 SHALL 通过统一档案记录主表固定字段 `fonds_code` 过滤记录
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

### Requirement: 管理查询与全文检索边界
系统 SHALL 区分管理数据查询和普通用户全文发现能力。

#### Scenario: 管理查询拒绝全文关键词
- **WHEN** 客户端提交 `keyword`
- **THEN** 系统 SHALL 拒绝档案管理列表查询
- **AND** 响应 SHALL 说明管理查询只支持数据库字段筛选

#### Scenario: 管理查询不依赖全文检索中间件
- **WHEN** 客户端按分类、全宗、精确字段或范围字段查询档案管理列表
- **THEN** 系统 SHALL 使用数据库主表和分类动态表执行筛选
- **AND** 系统 SHALL NOT 访问 Elasticsearch、OpenSearch、Solr、Meilisearch 或其他全文检索 adapter 作为必要步骤

#### Scenario: 普通用户全文发现能力可选
- **WHEN** 查档、借阅或利用服务类入口需要全文发现能力
- **THEN** 系统 SHALL 在普通用户搜索查询中同时执行全文条件、结构化筛选、权限判断和逻辑删除判断
- **AND** 全文检索 SHALL 通过 `archive.search.full-text.adapter` 支持 `disabled`、`postgresql` 或已注册 adapter 配置
- **AND** 核心业务代码 SHALL NOT 绑定某一个全文检索中间件产品

#### Scenario: 普通用户搜索直接合并过滤条件
- **WHEN** 客户端提交 `keyword`、全宗和字段筛选条件
- **THEN** 系统 SHALL 在同一搜索执行路径中合并全文条件、结构化筛选、全宗筛选、权限判断和逻辑删除判断
- **AND** 系统 SHALL NOT 先从全文检索 adapter 召回裸 ID 再由业务代码二次过滤
- **AND** 系统 SHALL 在最终查询阶段排除已逻辑删除记录和当前用户不可见记录

#### Scenario: 未启用全文检索时执行管理查询
- **WHEN** 全文检索 adapter 为 `disabled`
- **THEN** 系统 SHALL 允许管理列表按数据库字段筛选
- **AND** 系统 SHALL NOT 访问全文投影表作为必要步骤

#### Scenario: 启用全文检索但依赖缺失
- **WHEN** 全文检索 adapter 为 `postgresql` 且数据库缺少 `pg_trgm` 扩展或全文检索索引
- **THEN** 系统 SHALL 在启动阶段 fail-fast
- **WHEN** 全文检索 adapter 不是 `disabled` 且未注册
- **THEN** 系统 SHALL 在启动阶段 fail-fast
