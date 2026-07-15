# archive-item-search Specification

## Purpose

定义档案条目查询、后台管理筛选、普通用户全文发现、全文投影维护和全文 provider 选择的业务合同，确保不同入口共享清晰的一致性、权限和逻辑删除语义。

## Requirements

### Requirement: 管理查询与用户全文发现边界

系统 SHALL 区分后台管理数据查询和普通用户发现型搜索。

#### Scenario: 管理查询使用数据库语义

- **WHEN** 客户端查询档案管理列表、后台筛选、排序、权限过滤、精确字段筛选或统计数据
- **THEN** 系统 SHALL 使用数据库主表、分类动态表和结构化条件执行查询
- **AND** 系统 SHALL NOT 通过 Elasticsearch、OpenSearch、Solr、Meilisearch 或其他全文检索实现作为必要步骤
- **AND** 管理查询 SHALL NOT 提供全文 provider 切换配置

#### Scenario: 管理查询拒绝全文关键词

- **WHEN** 客户端向档案管理列表查询提交 `keyword`
- **THEN** 系统 SHALL 拒绝查询
- **AND** 响应 SHALL 说明管理查询只支持数据库字段筛选

#### Scenario: 普通用户全文搜索合并业务过滤

- **WHEN** 查档、借阅或利用服务类入口提交全文关键词、全宗、结构化字段、权限和逻辑删除条件
- **THEN** 系统 SHALL 在同一查询语义中合并全文条件、结构化筛选、权限判断和逻辑删除判断
- **AND** 系统 SHALL NOT 先从全文 provider 召回裸 ID 再由业务代码二次过滤
- **AND** 最终结果 SHALL 排除已逻辑删除条目和当前用户不可见记录

### Requirement: 全文检索 provider

全文检索 SHALL 通过 provider 机制切换具体实现，默认使用 PostgreSQL。

#### Scenario: 默认 PostgreSQL provider

- **WHEN** 未显式配置全文 provider
- **THEN** 系统 SHALL 使用 `postgresql` provider
- **AND** PostgreSQL provider SHALL 使用 `pg_trgm`、GIN 索引和 `ILIKE` 支持前后模糊匹配

#### Scenario: 配置全文 provider

- **WHEN** 配置 `archive.search.full-text.provider`
- **THEN** 系统 SHALL 按配置选择已注册 provider
- **AND** 后续新增 provider SHALL 像 Spring Session 或 Spring Cache 一样通过标准 Bean 和配置切换
- **AND** 核心业务查询代码 SHALL NOT 绑定某一个全文检索中间件产品
- **AND** 系统 SHALL NOT 提供 `disabled` 作为普通用户全文发现能力的业务开关

#### Scenario: provider 依赖缺失

- **WHEN** `postgresql` provider 所需的 `pg_trgm` 扩展或全文检索索引缺失
- **THEN** 系统 SHALL 在启动阶段 fail-fast
- **WHEN** 配置的 provider 未注册
- **THEN** 系统 SHALL 在启动阶段 fail-fast

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

### Requirement: 条目全文投影

系统 SHALL 为全文检索维护独立投影表，并以所有启用动态字段生成投影文本。

#### Scenario: 创建档案条目后维护投影

- **WHEN** 客户端创建档案条目且分类存在启用的动态字段
- **THEN** 系统 SHALL 将所有启用动态字段的字段名称和值拼接为 `search_text`
- **AND** 全文投影表 SHALL 只保存条目 ID、`search_text`、索引版本和投影维护时间

#### Scenario: 条目投影包含明细行

- **WHEN** 系统维护条目全文投影
- **THEN** `search_text` SHALL 包含条目固定字段、条目分类动态字段、条目实物字段和条目明细行文本
- **AND** 系统 SHALL NOT 将关联条目的全文内容拼入当前条目投影

#### Scenario: 删除档案条目后删除投影

- **WHEN** 客户端删除档案条目
- **THEN** 系统 SHALL 删除该记录对应的全文投影行
- **AND** 系统 SHALL NOT 依赖全文投影表保存删除状态

#### Scenario: 动态字段定义变更

- **WHEN** 客户端新增、启用或重命名动态字段定义
- **THEN** 系统 SHALL NOT 阻塞字段定义保存来同步重建历史投影
- **AND** 系统 SHALL 允许通过单独重建流程补齐历史投影

### Requirement: 案卷全文投影

系统 SHALL 为 archive volume 维护独立全文投影。

#### Scenario: 案卷投影不拼接卷内条目

- **WHEN** 系统维护案卷全文投影
- **THEN** `search_text` SHALL 只包含案卷自身固定字段、分类动态字段和实物字段
- **AND** 系统 SHALL NOT 将案卷下所有条目全文拼入案卷投影

### Requirement: 条目关联检索边界

条目关联 SHALL 作为结构化关系查询能力，不参与全文投影拼接。

#### Scenario: 关联展示限制深度

- **WHEN** 客户端读取条目详情或关联图
- **THEN** 系统 SHALL 默认只返回一层直接关联
- **AND** 关联图最大深度 SHALL 不超过 2
- **AND** 系统 SHALL 对关联目标执行权限过滤并防止循环展开
