# 能力规格：档案记录搜索

构建命令、代码风格、测试策略和工程边界见[项目总规格](../SPEC.md)。

## 目标

定义档案条目查询、后台管理筛选、普通用户全文发现、全文投影维护和全文 provider 选择的业务合同，确保不同入口共享清晰的一致性、权限和逻辑删除语义。

## 验收要求

### 要求： 管理查询与用户全文发现边界

系统 SHALL 区分后台管理数据查询和普通用户发现型搜索。

#### 场景： 管理查询使用数据库语义

- **WHEN** 客户端查询档案管理列表、后台筛选、排序、权限过滤、精确字段筛选或统计数据
- **THEN** 系统 SHALL 使用数据库主表、分类动态表和结构化条件执行查询
- **AND** 系统 SHALL NOT 通过 Elasticsearch、OpenSearch、Solr、Meilisearch 或其他全文检索实现作为必要步骤
- **AND** 管理查询 SHALL NOT 提供全文 provider 切换配置

#### 场景： 管理查询拒绝全文关键词

- **WHEN** 客户端向档案管理列表查询提交 `keyword`
- **THEN** 系统 SHALL 拒绝查询
- **AND** 响应 SHALL 说明管理查询只支持数据库字段筛选

#### 场景： 管理查询应用当前用户数据范围

- **WHEN** 用户查询档案管理列表
- **THEN** 系统 SHALL 使用 `deleted_flag=false AND 用户数据范围 AND 用户查询条件` 查询档案
- **AND** 系统 SHALL 返回同时命中数据范围和查询条件的档案记录
- **AND** 系统 SHALL NOT 返回用户数据范围外档案

#### 场景： 数据范围与用户条件冲突

- **WHEN** 用户查询条件与用户数据范围没有交集
- **THEN** 系统 SHALL 返回空结果
- **AND** 系统 SHALL NOT 放宽用户数据范围以满足查询条件

#### 场景： 普通用户全文搜索合并业务过滤

- **WHEN** 查档、借阅或利用服务类入口提交全文关键词、全宗、结构化字段、权限和逻辑删除条件
- **THEN** 系统 SHALL 在同一查询语义中合并全文条件、结构化筛选、权限判断和逻辑删除判断
- **AND** 系统 SHALL NOT 先从全文 provider 召回裸 ID 再由业务代码二次过滤
- **AND** 最终结果 SHALL 排除已逻辑删除条目和当前用户不可见记录

#### 场景： 未认证用户全文发现

- **WHEN** 未认证用户请求全文发现
- **THEN** 系统 SHALL 拒绝请求
- **AND** 系统 SHALL NOT 返回任何档案记录

### 要求： 全文检索 provider

全文检索 SHALL 通过 provider 机制切换具体实现，默认使用 PostgreSQL。

#### 场景： 默认 PostgreSQL provider

- **WHEN** 未显式配置全文 provider
- **THEN** 系统 SHALL 使用 `postgresql` provider
- **AND** PostgreSQL provider SHALL 使用 `pg_trgm`、GIN 索引和 `ILIKE` 支持前后模糊匹配

#### 场景： 配置全文 provider

- **WHEN** 配置 `archive.search.full-text.provider`
- **THEN** 系统 SHALL 按配置选择已注册 provider
- **AND** 后续新增 provider SHALL 像 Spring Session 或 Spring Cache 一样通过标准 Bean 和配置切换
- **AND** 核心业务查询代码 SHALL NOT 绑定某一个全文检索中间件产品
- **AND** 系统 SHALL NOT 提供 `disabled` 作为普通用户全文发现能力的业务开关

#### 场景： provider 依赖缺失

- **WHEN** `postgresql` provider 所需的 `pg_trgm` 扩展或全文检索索引缺失
- **THEN** 系统 SHALL 在启动阶段 fail-fast
- **WHEN** 配置的 provider 未注册
- **THEN** 系统 SHALL 在启动阶段 fail-fast

### 要求： 档案条目高级查询

系统 SHALL 使用统一高级查询条件树表达后台管理筛选和普通用户结构化筛选。

#### 场景： 使用字段操作符过滤

- **WHEN** 客户端提交包含 `where.conditions` 的档案条目查询请求
- **THEN** 系统 SHALL 只接受字段定义中 `exact_searchable=true` 或唯一约束索引覆盖的字段编码
- **AND** 系统 SHALL 使用字段定义解析出的动态列名构造查询条件
- **AND** 系统 SHALL 使用参数绑定传递筛选值
- **AND** 系统 SHALL 对文本输入执行 `trimToNull`
- **AND** 系统 SHALL 使用大写枚举值表达操作符，例如 `EQ`、`CONTAINS`、`STARTS_WITH`、`GTE`、`LTE`、`BETWEEN`、`IS_EMPTY`、`IS_NOT_EMPTY`
- **AND** 系统 SHALL 按字段类型限制可用操作符和前端控件
- **AND** `where.conditions` SHALL 固定按 `AND` 组合
- **AND** 请求体 SHALL NOT 提供 `logic` 字段切换条件组合方式

#### 场景： 使用不允许精确筛选的字段过滤

- **WHEN** 客户端提交的 `where.conditions` 包含未启用精确筛选且未被唯一约束索引覆盖的字段
- **THEN** 系统 SHALL 拒绝查询
- **AND** 响应 SHALL 说明该字段不允许作为筛选条件

#### 场景： 按固定字段或可搜索动态字段排序

- **WHEN** 客户端通过 URL query 参数提交 `sort`
- **THEN** `sort` SHALL 支持固定排序字段编码或可搜索动态字段编码，并按 `+field,-field` 表示多列优先顺序
- **AND** cursor 搜索接口 SHALL 在 JSON 请求体中仅提交业务查询条件
- **AND** 可搜索动态字段 SHALL 指字段定义中 `exact_searchable=true` 或唯一约束索引覆盖的字段
- **AND** 系统 SHALL 使用字段元数据将动态字段编码映射为当前分类动态表列名
- **AND** 系统 SHALL 拒绝不可搜索动态字段排序
- **AND** 允许排序且 `list_visible=false` 的动态字段 SHALL 只参与内部投影和游标取键，不作为可见动态值返回
- **AND** 可空排序字段 SHALL 在升序中将空值放在最后，在降序中将空值放在最前
- **AND** 系统 SHALL 保留复合游标中空值的位置，并以相同空值顺序执行下一页和上一页查询
- **AND** 多列排序值相同或包含空值时，稳定兜底排序 SHALL 保证完整双向遍历没有重复或遗漏

#### 场景： 使用关联分类分组过滤

- **WHEN** 客户端提交 `relatedGroups`
- **THEN** 每个关联分组 SHALL 使用系统按当前分类派生出的关联档案分类
- **AND** 系统 SHALL 将该分组编译为当前条目查询上的结构化 `exists` 条件
- **AND** 关联分组 SHALL 使用关联分类自己的字段元数据、操作符和控件规则
- **AND** 关联方向 SHALL 由关联分类派生结果默认带出，不作为用户筛选控件
- **AND** 关联方向 SHALL 使用大写枚举值 `OUTGOING` 或 `BOTH`
- **AND** 多个关联分组 SHALL 默认按 `AND` 组合

#### 场景： 获取可用于关联筛选的分类

- **WHEN** 客户端请求当前档案分类的关联筛选分类
- **THEN** 系统 SHALL 返回当前分类作为来源分类时已关联出去的目标分类
- **AND** 若同一分类对同时存在反向关联，系统 SHALL 返回方向 `BOTH`
- **AND** 系统 SHALL NOT 返回仅关联到当前分类的 `INCOMING` 分类作为默认高级筛选分组

#### 场景： 按全宗固定字段过滤

- **WHEN** 客户端提交全宗编码筛选条件
- **THEN** 系统 SHALL 通过 `am_archive_item` 固定字段 `fonds_code` 过滤记录
- **AND** 系统 SHALL NOT 要求 `fonds_code` 在字段定义表中存在

#### 场景： 按所属案卷固定字段过滤

- **WHEN** 客户端在 `SearchArchiveItemsRequest` 中提交可空 `volumeId`
- **THEN** 系统 SHALL 通过 `am_archive_item.volume_id` 过滤指定案卷内的未删除档案
- **AND** `volumeId` SHALL 作为业务筛选字段进入 JSON 请求体和 cursor 查询摘要
- **AND** 带 cursor 的后续请求 SHALL 重复提交与首次查询相同的 `volumeId`
- **AND** URL query 中的 `limit`、`cursor` SHALL 作为分页控制字段；需要首页总数时使用 `Prefer: return=total-count`
- **AND** `limit`、`cursor` 和总数偏好 SHALL NOT 进入 cursor 查询摘要

### 要求： 条目全文投影

系统 SHALL 为 archive item 全文检索维护独立投影表，并以启用的 `METADATA` 动态字段和条目明细行文本生成投影文本。

#### 场景： 创建档案条目后维护投影

- **WHEN** 客户端创建档案条目且分类存在启用的 `field_scope=METADATA` item 动态字段
- **THEN** 系统 SHALL 将这些动态字段的字段名称和值拼接为 `search_text`
- **AND** 全文投影表 SHALL 只保存条目 ID、`search_text`、索引版本和投影维护时间

#### 场景： 条目投影包含明细行

- **WHEN** 系统维护条目全文投影
- **THEN** `search_text` SHALL 包含该分类下所有启用的 `field_scope=METADATA` item 动态字段名称和值
- **AND** `search_text` SHALL 包含该条目未删除明细行的字段名称和值
- **AND** 系统 SHALL NOT 将关联条目的全文内容拼入当前条目投影

#### 场景： 删除档案条目后删除投影

- **WHEN** 客户端删除档案条目
- **THEN** 系统 SHALL 删除该记录对应的全文投影行
- **AND** 系统 SHALL NOT 依赖全文投影表保存删除状态

#### 场景： 动态字段定义变更

- **WHEN** 客户端新增、启用或重命名动态字段定义
- **THEN** 系统 SHALL NOT 阻塞字段定义保存来同步重建历史投影
- **AND** 系统 SHALL 允许通过单独重建流程补齐历史投影

### 要求： 搜索投影重建任务

系统 SHALL 通过可恢复的异步任务重建分类下的档案条目搜索投影。

#### 场景： 启动搜索投影重建任务

- **WHEN** 具有档案元数据管理权限的用户调用 `POST /archive-categories/{categoryId}:rebuildSearchProjection`
- **THEN** 系统 SHALL 在任务可靠入库后返回 `202 Accepted` 和操作监视资源表示
- **AND** 响应头 SHALL 提供字符串 `Operation-Id`、指向 `/operations/{id}` 的绝对 `Operation-Location` 以及 `Retry-After`
- **AND** 响应体 SHALL 使用操作监视资源的 `id`、`status` 和 `kind`，不返回旧任务资源路径
- **AND** 系统 SHALL 冻结任务创建时待处理档案条目的 ID 上界
- **AND** 系统 SHALL NOT 在启动请求内同步遍历该分类全部档案条目

#### 场景： 分批执行搜索投影重建任务

- **WHEN** 后台处理搜索投影重建任务
- **THEN** 系统 SHALL 使用有界批次处理任务创建时 ID 上界内的全部未删除档案条目
- **AND** 每个批次成功后系统 SHALL 持久化处理进度和最近完成的档案条目 ID
- **AND** 应用重启后系统 SHALL 从最近完成的档案条目 ID 继续处理
- **AND** 全部批次完成前任务 SHALL NOT 进入 `Succeeded`

#### 场景： 重新登录后找回搜索投影重建任务

- **WHEN** 发起人关闭浏览器、刷新页面或会话过期后重新登录，且仍具有档案元数据管理权限
- **THEN** 系统 SHALL 允许其通过 `GET /operations` 分页列出自己发起的搜索投影重建任务，并通过 `GET /operations/{id}` 查询状态
- **AND** 列表 SHALL 按任务 ID 倒序返回 `items`、`self` 及可用时的 `prev`、`next` 链接，使用不透明 `cursor`；`limit` 默认 20、最大 100，不计算总数
- **AND** 状态资源 SHALL 使用 Azure 的 `id`、`status`、`kind`、失败时的 `error`、成功时的 `result`；未结束时 SHALL 返回 `Retry-After`
- **AND** 成功任务的 `result` SHALL 包含分类 ID 和实际重建数量，失败任务的 `error` SHALL 包含稳定的 `code` 和可展示的 `message`
- **AND** 系统 SHALL 在每次列表或状态查询时重新校验登录身份和档案元数据管理权限；状态资源只向发起人展示，其他用户得到 `404`
- **AND** 浏览器关闭或退出登录 SHALL NOT 取消已受理任务；当前任务记录与结果摘要不自动清理，状态资源至少保留 24 小时，失败后再次发起将创建新任务

### 要求： 条目关联检索边界

条目关联 SHALL 作为结构化关系查询能力，不参与全文投影拼接。

#### 场景： 关联展示限制深度

- **WHEN** 客户端读取条目详情或关联图
- **THEN** 系统 SHALL 默认只返回一层直接关联
- **AND** 关联图最大深度 SHALL 不超过 2
- **AND** 系统 SHALL 对关联目标执行权限过滤并防止循环展开

### 要求： 档案详情读取

系统 SHALL 在读取档案详情时校验当前用户功能权限和数据范围。

#### 场景： 读取范围内档案详情

- **WHEN** 用户读取其数据范围内的档案详情
- **THEN** 系统 SHALL 返回档案固定字段、动态字段和布局信息

#### 场景： 拒绝读取范围外档案详情

- **WHEN** 用户读取其数据范围外的档案详情
- **THEN** 系统 SHALL 拒绝请求
- **AND** 响应 SHALL 使用项目统一 ProblemDetail 错误模型
