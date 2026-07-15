# archive-metadata Specification

## Purpose

定义档案元数据、分类动态表、动态字段、唯一规则、项目自有表命名和逻辑删除相关业务数据合同，作为档案条目建表、保存、删除和约束维护的验收真相源。
## Requirements
### Requirement: 项目自有数据库对象命名

项目自有数据库对象 SHALL 带有模块语义并使用小写 snake_case。

#### Scenario: 新建项目自有表

- **WHEN** Flyway 迁移中新建业务表、平台表、审计表或中间表
- **THEN** 表名 SHALL 使用 `am_模块_表名` 格式
- **AND** 系统 SHALL NOT 使用缺少模块语义的 `am_表名` 格式
- **AND** 索引、约束和序列等对象名称 SHALL 跟随表名保留 `am_模块_` 语义

#### Scenario: 保留第三方框架原生表

- **WHEN** 迁移 Spring Session、Quartz、Flowable 等第三方框架原生表
- **THEN** 系统 SHALL 保留框架默认表名和对象名
- **AND** 系统 SHALL NOT 为项目命名规范重命名上游原生表

### Requirement: 项目自有表字段和校验

项目自有表 SHALL 将业务校验放在应用层、字典或配置层表达。

#### Scenario: 新建项目自有表

- **WHEN** Flyway 迁移创建项目自有表
- **THEN** 迁移 SHALL NOT 为枚举、状态或数值范围创建数据库 `CHECK` 约束
- **AND** 对应校验 SHALL 由应用层、字典或配置层执行

#### Scenario: 保存实体枚举字段

- **WHEN** 实体字段使用 Java enum 表达业务枚举
- **THEN** enum 常量名 SHALL 使用对外和入库一致的大写可读字符串，多个英文单词使用下划线分隔
- **AND** 实体属性 SHALL 标注 `@Enumerated(EnumType.STRING)`
- **AND** 系统 SHALL NOT 依赖 `enum.ordinal()`
- **AND** 系统 SHALL NOT 为简单枚举手写 `AttributeConverter` 或额外注册 Jackson/MVC 枚举转换器
- **AND** DDL 的 `comment on column` SHALL 列出每个枚举值及其含义

#### Scenario: 保存项目自有时间字段

- **WHEN** 项目自有表保存业务时间字段
- **THEN** 字段类型 SHALL 使用无时区 `timestamp`
- **AND** 系统 SHALL NOT 为项目自有表使用 `timestamptz`

### Requirement: 逻辑删除唯一性

带逻辑删除字段的项目自有表 SHALL 只约束未删除记录的唯一性。

#### Scenario: 新建逻辑删除表唯一规则

- **WHEN** 项目自有表包含逻辑删除字段且需要唯一性约束
- **THEN** 系统 SHALL 使用部分唯一索引只约束未删除记录
- **AND** 系统 SHALL NOT 创建覆盖已删除数据的普通唯一约束

#### Scenario: 删除记录后释放唯一值

- **WHEN** 客户端删除一条占用唯一值的记录
- **THEN** 系统 SHALL 将记录标记为已逻辑删除
- **AND** 系统 SHALL 允许后续记录重新使用该唯一值

### Requirement: 分类动态表边界和路由

系统 SHALL 按 item/volume 对象类型和字段域创建分类动态表，并只保存对象关联、动态业务字段和固定行维护列。

#### Scenario: 分类动态表不复制主表系统字段

- **WHEN** 系统创建分类动态数据表
- **THEN** 动态表 SHALL 固定包含对象 ID、`deleted_flag`、`deleted_at`、`deleted_by`、`created_at` 和 `updated_at`
- **AND** 动态表 SHALL NOT 复制主表的全宗、分类、档号、状态、锁定和随机分桶字段
- **AND** 业务动态字段 SHALL NOT 复用固定维护列名

#### Scenario: 按对象类型和字段域路由动态表

- **WHEN** 系统为分类的某个对象类型和字段域创建动态表
- **THEN** item 动态表 SHALL 使用 `id` 引用 `am_archive_item`
- **AND** volume 动态表 SHALL 使用 `id` 引用 `am_archive_volume`
- **AND** `METADATA` 与 `PHYSICAL` 字段 SHALL 分别进入该对象的元数据表和实物信息表
- **AND** 系统 SHALL NOT 将 item、volume、元数据和实物信息混写到同一张动态表

#### Scenario: 动态表名使用稳定业务键

- **WHEN** 系统为档案分类、对象类型和字段域生成默认动态表名
- **THEN** 表名 SHALL 基于分类编码、item/volume 对象类型和 `field_scope` 这些稳定业务键生成
- **AND** 表名 SHALL NOT 使用数据库自增 ID 作为必要组成部分
- **AND** 表名 SHALL 使用小写 snake_case 且不超过 PostgreSQL 63 字节标识符限制
- **AND** 当稳定业务键过长时，系统 SHALL 使用稳定哈希后缀生成可重复的短表名

#### Scenario: 分层创建电子和实物动态表

- **WHEN** 档案分类启用 `ITEM_ONLY` 管理模式
- **THEN** 系统 SHALL 至少支持卷内条目的元数据表
- **AND** 系统 SHALL 在存在卷内实物字段时支持独立的卷内实物信息表
- **WHEN** 档案分类启用 `VOLUME_ITEM` 管理模式
- **THEN** 系统 SHALL 支持案卷元数据、案卷实物、卷内元数据和卷内实物四类动态表
- **AND** 系统 SHALL NOT 强制案卷和卷内共用同一套实物字段

#### Scenario: 逻辑删除条目及其动态数据

- **WHEN** 客户端删除档案条目
- **THEN** 系统 SHALL 将 `am_archive_item.deleted_flag` 标记为 `true`
- **AND** 系统 SHALL 写入 `am_archive_item.deleted_at` 和 `am_archive_item.deleted_by`
- **AND** 系统 SHALL 将该条目已建元数据表和实物信息表对应行的 `deleted_flag` 标记为 `true`
- **AND** 系统 SHALL 写入对应动态行的 `deleted_at` 和 `deleted_by`
- **AND** 系统 SHALL 允许动态表部分唯一索引释放该记录占用的唯一值
- **AND** 回收站查询 SHALL 使用 `deleted_at DESC, id DESC` 作为稳定默认排序

### Requirement: 条目与案卷分对象

系统 SHALL 使用独立 archive item 和 archive volume 对象表达卷内条目与案卷。

#### Scenario: 条目主表不保存层级字段

- **WHEN** Flyway 创建档案条目主表
- **THEN** 系统 SHALL 使用 `am_archive_item` 保存条目固定字段
- **AND** `am_archive_item` SHALL 使用 `volume_id` 引用所属案卷
- **AND** `am_archive_item` SHALL NOT 保存 `archive_level` 或 `parent_id`

#### Scenario: 案卷独立保存

- **WHEN** Flyway 创建案卷主表
- **THEN** 系统 SHALL 使用 `am_archive_volume` 保存案卷固定字段
- **AND** 系统 SHALL NOT 将案卷作为 `am_archive_item` 中的一行保存

#### Scenario: 主表保存随机抽查辅助分桶

- **WHEN** Flyway 创建档案条目和案卷主表
- **THEN** `am_archive_item` 和 `am_archive_volume` SHALL 保存 `random_bucket`
- **AND** `random_bucket` SHALL 是创建时生成的稳定辅助分桶
- **AND** `random_bucket` SHALL 约束在 `[0, 10000)` 范围内
- **AND** 系统 SHALL 为未删除记录提供 `random_bucket` 查询索引
- **AND** 分类动态表 SHALL NOT 保存 `random_bucket`

### Requirement: 条目关联

系统 SHALL 仅支持 archive item 到 archive item 的结构化关联。

#### Scenario: 创建条目关联

- **WHEN** 客户端为条目创建关联档案
- **THEN** 系统 SHALL 保存 `id`、`source_item_id`、`target_item_id` 和 `created_at`
- **AND** 系统 SHALL 拒绝条目关联自身
- **AND** 系统 SHALL 使用唯一索引防止重复关联

### Requirement: 条目明细表定义

系统 SHALL 支持在档案分类下定义 archive item 明细表。

#### Scenario: 定义条目明细表

- **WHEN** 客户端为分类创建条目明细表定义
- **THEN** 系统 SHALL 保存明细表编码、名称、动态物理表名和排序
- **AND** 明细表 SHALL 归属于分类，不作为独立档案分类

#### Scenario: 构建条目明细动态表

- **WHEN** 客户端对有字段的条目明细表执行建表动作
- **THEN** 系统 SHALL 创建动态明细表
- **AND** 动态明细表 SHALL 使用 `item_id` 引用 `am_archive_item`
- **AND** 动态明细表 SHALL 支持同一条目下多行明细
- **AND** 动态明细表 SHALL 固定包含 `id`、`item_id`、`line_order`、`deleted_flag`、`deleted_at`、`deleted_by`、`created_at` 和 `updated_at`
- **AND** 系统 SHALL 为未删除明细行提供按 `item_id`、`line_order` 和 `id` 查询的索引

#### Scenario: 逻辑删除条目明细行

- **WHEN** 客户端删除一条档案条目明细行
- **THEN** 系统 SHALL 将该明细行的 `deleted_flag` 标记为 `true`
- **AND** 系统 SHALL 写入该明细行的 `deleted_at` 和 `deleted_by`
- **AND** 未删除明细行 SHALL 按 `line_order` 和 `id` 稳定排序

### Requirement: 字段检索标记

系统 SHALL 在字段定义中只暴露精确筛选标记，全文检索不暴露字段级开关。

#### Scenario: 字段定义区分字段域

- **WHEN** 客户端为档案分类新增字段
- **THEN** 字段定义 SHALL 保存 item/volume 适用对象
- **AND** 字段定义 SHALL 保存 `field_scope`
- **AND** `field_scope=METADATA` SHALL 表示案卷或卷内电子字段
- **AND** `field_scope=PHYSICAL` SHALL 表示案卷或卷内实物信息字段

#### Scenario: 创建字段定义

- **WHEN** 客户端为档案分类新增字段
- **THEN** 系统 SHALL 保存该字段是否允许精确筛选
- **AND** 系统 SHALL NOT 保存该字段是否进入全文检索投影的字段级配置
- **AND** 系统 SHALL NOT 使用单一 `searchable` 字段同时表达精确筛选和全文检索

#### Scenario: 全文投影字段来源

- **WHEN** 系统维护条目全文检索投影
- **THEN** 系统 SHALL 固定读取该分类下所有启用的 `field_scope=METADATA` item 动态字段名称和值
- **AND** 系统 SHALL 读取该条目未删除明细行的字段名称和值
- **AND** 系统 SHALL NOT 要求客户端为字段配置全文检索开关

#### Scenario: 使用固定记录字段编码创建动态字段

- **WHEN** 客户端使用 `fonds_code`、`fonds_name`、`archive_no`、`archive_year`、`archive_status`、`process_status` 等档案条目主表字段编码创建分类字段
- **THEN** 系统 SHALL 拒绝保存
- **AND** 响应 SHALL 说明该字段编码属于档案条目固定字段，不能作为动态字段

### Requirement: 精确筛选索引维护

系统 SHALL 为允许精确筛选的字段维护动态表普通索引。

#### Scenario: 建表时创建精确筛选索引

- **WHEN** 客户端对分类执行建表动作且字段定义包含 `exact_searchable=true` 的字段
- **THEN** 系统 SHALL 为该字段对应动态列创建普通索引
- **AND** 索引 SHALL 只在动态表中对未删除记录生效

#### Scenario: 字段改为精确筛选

- **WHEN** 客户端将已建表字段改为允许精确筛选
- **THEN** 系统 SHALL 在下一次生成或更新表结构时补齐该字段普通索引

### Requirement: 分类唯一规则管理

系统 SHALL 支持按档案分类维护唯一规则。

#### Scenario: 创建唯一规则

- **WHEN** 客户端为某个档案分类提交唯一规则名称、编码和字段组合
- **THEN** 系统 SHALL 保存唯一规则
- **AND** 系统 SHALL 只允许选择该分类下未删除、当前约束层级一致且 `field_scope=METADATA` 的案卷或卷内电子字段
- **AND** 系统 SHALL NOT 允许实物信息字段参与唯一校验
- **AND** 如果分类未启用案卷管理，系统 SHALL NOT 允许创建案卷层级唯一规则
- **AND** 系统 SHALL 按客户端提交的字段顺序保存规则字段

#### Scenario: 禁用唯一规则

- **WHEN** 客户端禁用某条唯一规则
- **THEN** 系统 SHALL 保留规则定义
- **AND** 系统 SHALL 删除或停用该规则对应的动态表唯一索引

### Requirement: 动态唯一索引

系统 SHALL 通过分类动态表部分唯一索引执行唯一规则。

#### Scenario: 动态唯一索引名使用稳定业务键

- **WHEN** 系统为分类唯一规则创建动态唯一索引
- **THEN** 索引名 SHALL 基于分类编码、item/volume 对象类型 和唯一规则编码这些稳定业务键生成
- **AND** 索引名 SHALL NOT 使用数据库自增 ID 作为必要组成部分
- **AND** 索引名 SHALL 不超过 PostgreSQL 63 字节标识符限制
- **AND** 当稳定业务键过长时，系统 SHALL 使用稳定哈希后缀生成可重复的短索引名

#### Scenario: 创建动态字段唯一索引

- **WHEN** 唯一规则包含一个或多个案卷或卷内电子字段
- **THEN** 系统 SHALL 在该分类动态表上按规则字段顺序创建唯一索引
- **AND** 唯一索引 SHALL 使用 `where deleted_flag = false` 只约束未删除动态行

#### Scenario: 使用动态字段组合表达范围唯一

- **WHEN** 客户端需要部门内或其他业务范围内唯一
- **THEN** 系统 SHALL 允许客户端选择部门等电子范围字段和电子业务字段共同组成唯一规则
- **AND** 系统 SHALL NOT 提供全宗固定字段特殊唯一范围开关

#### Scenario: 空值唯一语义

- **WHEN** 唯一规则字段值为 `NULL`
- **THEN** 系统 SHALL 使用 PostgreSQL 默认唯一索引语义
- **AND** 系统 SHALL NOT 将多个 `NULL` 值视为互相冲突

### Requirement: 唯一冲突响应

系统 SHALL 在档案条目保存时返回明确的唯一冲突错误。

#### Scenario: 创建记录触发唯一冲突

- **WHEN** 客户端创建档案条目导致分类动态表唯一索引冲突
- **THEN** 系统 SHALL 拒绝保存
- **AND** 响应 SHALL 说明违反唯一规则

### Requirement: 全宗管理

系统 SHALL 提供全宗管理能力，全宗作为档案记录归属维度，不作为档案分类字段模板的一部分。

#### Scenario: 创建全宗

- **WHEN** 客户端提交全宗编码和全宗名称
- **THEN** 系统 SHALL 创建一条全宗记录
- **AND** 全宗编码 SHALL 在未删除记录中唯一

#### Scenario: 查询启用全宗

- **WHEN** 客户端查询全宗列表
- **THEN** 系统 SHALL 返回未删除全宗
- **AND** 系统 SHALL 支持按启用状态筛选

#### Scenario: 禁用全宗

- **WHEN** 客户端禁用一个全宗
- **THEN** 系统 SHALL 保留历史档案记录上的全宗编码和名称
- **AND** 系统 SHALL NOT 删除该全宗关联的档案记录

### Requirement: 档案分类管理

系统 SHALL 提供多层级档案分类管理能力，档案分类作为字段模板和动态表路由源，不绑定具体全宗，不单独维护门类或分类分组。

#### Scenario: 创建档案分类

- **WHEN** 客户端提交分类编码和分类名称
- **THEN** 系统 SHALL 创建一条档案分类记录
- **AND** 分类编码 SHALL 在未删除记录中唯一
- **AND** 系统 SHALL NOT 要求选择全宗
- **AND** 系统 SHALL 允许选择一个未删除档案分类作为父级分类

#### Scenario: 拒绝重复分类编码

- **WHEN** 客户端创建分类或将分类编码修改为其他未删除分类已占用的编码
- **THEN** 系统 SHALL 拒绝保存
- **AND** 响应 SHALL 使用 `409 Conflict` 和 `ALREADY_EXISTS` ProblemDetail
- **AND** 已逻辑删除分类 SHALL NOT 继续占用分类编码

#### Scenario: 查询档案分类

- **WHEN** 客户端查询档案分类列表
- **THEN** 系统 SHALL 返回分类编码、分类名称和父级分类 ID
- **AND** 系统 SHALL 分别返回案卷元数据表名 `volumeTableName`、卷内元数据表名 `itemTableName`、案卷实物表名 `volumePhysicalTableName` 和卷内实物表名 `itemPhysicalTableName`
- **AND** 系统 SHALL 返回建表状态 `tableStatus` 和更新时间

#### Scenario: 禁止分类循环

- **WHEN** 客户端创建或修改档案分类父级
- **THEN** 系统 SHALL 拒绝将分类自身设置为父级
- **AND** 系统 SHALL 拒绝将分类子孙设置为父级

#### Scenario: 删除存在子分类的分类

- **WHEN** 客户端删除仍存在未删除子分类的档案分类
- **THEN** 系统 SHALL 拒绝删除该分类

### Requirement: 档案字段定义

系统 SHALL 允许在档案分类下维护字段定义，用于驱动动态数据表和档案库列表。

#### Scenario: 创建字段定义

- **WHEN** 客户端为档案分类新增字段
- **THEN** 字段编码 SHALL 只允许小写字母、数字和下划线
- **AND** 字段类型 SHALL 只允许 `TEXT`、`INTEGER`、`DECIMAL`、`DATE`、`DATETIME`
- **AND** 同一分类下字段编码 SHALL 在未删除记录中唯一

#### Scenario: 配置字段展示

- **WHEN** 客户端修改字段名称、排序、是否列表显示或是否搜索
- **THEN** 系统 SHALL 更新字段定义运行时配置
- **AND** 系统 SHALL NOT 因展示配置变更修改物理列类型

#### Scenario: 枚举动态选项

- **WHEN** 某个字段需要枚举选项
- **THEN** 系统 SHALL 将枚举作为运行时动态选项处理
- **AND** 系统 SHALL NOT 将枚举作为独立物理列类型

### Requirement: 自动建表

系统 SHALL 按设置了字段定义的档案分类自动创建或增量维护动态数据表。

#### Scenario: 首次建表

- **WHEN** 客户端对某个对象类型和字段域中有启用字段且未建表的档案分类执行建表动作
- **THEN** 系统 SHALL 创建该分类对应的 item 或 volume 元数据表或实物信息表
- **AND** 动态表 SHALL 符合“分类动态表边界和路由”Requirement 的固定列和字段隔离合同
- **AND** 动态表主键 `id` SHALL 按对象类型引用 `am_archive_item.id` 或 `am_archive_volume.id`

#### Scenario: 增量加列

- **WHEN** 客户端对已建表分类执行建表动作且存在未建物理列的启用字段
- **THEN** 系统 SHALL 只追加缺失物理列
- **AND** 系统 SHALL NOT 删除或修改已存在物理列类型

#### Scenario: 无字段建表

- **WHEN** 客户端对没有启用字段的分类执行建表动作
- **THEN** 系统 SHALL 拒绝建表
- **AND** 响应 SHALL 说明该分类没有可建表字段

### Requirement: 字段编辑控件配置

系统 SHALL 在档案字段定义中保存编辑控件配置，并校验控件与字段类型兼容。

#### Scenario: 创建文本字段并配置多行文本

- **WHEN** 客户端为档案分类创建 `TEXT` 类型字段且编辑控件为 `TEXTAREA`
- **THEN** 系统 SHALL 保存该字段的编辑控件配置
- **AND** 后续字段查询 SHALL 返回该编辑控件配置

#### Scenario: 创建数字字段并配置非数字控件

- **WHEN** 客户端为 `INTEGER` 或 `DECIMAL` 类型字段配置 `INPUT`、`TEXTAREA`、`DATE` 或 `DATETIME` 控件
- **THEN** 系统 SHALL 拒绝保存
- **AND** 响应 SHALL 指出该字段类型只能使用数字控件

#### Scenario: 创建日期字段并配置错误控件

- **WHEN** 客户端为 `DATE` 类型字段配置非 `DATE` 控件，或为 `DATETIME` 类型字段配置非 `DATETIME` 控件
- **THEN** 系统 SHALL 拒绝保存
- **AND** 响应 SHALL 指出该字段类型可用的编辑控件

### Requirement: 独立布局配置

系统 SHALL 为档案分类提供独立公共布局配置，表格、详情和编辑布局 SHALL 分别维护字段顺序、显示状态和布局属性。

#### Scenario: 保存表格布局配置

- **WHEN** 客户端在表格布局中拖拽字段顺序并配置字段是否显示或列宽
- **THEN** 系统 SHALL 保存表格布局顺序、显示状态和列宽
- **AND** 系统 SHALL NOT 要求同时修改字段定义语义

#### Scenario: 保存详情和编辑布局配置

- **WHEN** 客户端在详情或编辑布局中拖拽字段顺序并配置字段是否显示或跨列
- **THEN** 系统 SHALL 保存对应布局的顺序、显示状态和跨列数
- **AND** 系统 SHALL NOT 因布局配置变更修改动态表物理列类型

#### Scenario: 公共布局兜底

- **WHEN** 当前分类和场景存在公共布局
- **THEN** 系统 SHALL 返回该公共布局作为有效布局
- **AND** 如果公共布局也不存在，系统 SHALL 使用字段定义中的默认展示配置生成有效布局

#### Scenario: 配置非法跨列值

- **WHEN** 客户端提交的详情跨列或编辑跨列不在系统允许范围内
- **THEN** 系统 SHALL 拒绝保存
- **AND** 响应 SHALL 指出跨列值不合法

#### Scenario: 配置非法列表列宽

- **WHEN** 客户端提交的列表列宽超出系统允许范围
- **THEN** 系统 SHALL 拒绝保存
- **AND** 响应 SHALL 指出列表列宽不合法

### Requirement: 唯一规则字段可筛选

系统 SHALL 将唯一规则中的案卷或卷内电子字段作为档案记录精确筛选字段维护。

#### Scenario: 创建唯一规则

- **WHEN** 客户端为档案分类创建唯一规则并选择字段
- **THEN** 系统 SHALL 将这些字段标记为允许精确筛选
- **AND** 系统 SHALL 为已建表分类维护这些字段的精确筛选索引

#### Scenario: 修改唯一规则字段

- **WHEN** 客户端修改唯一规则选择的字段
- **THEN** 系统 SHALL 将新选择字段标记为允许精确筛选
- **AND** 系统 SHALL 为已建表分类维护这些字段的精确筛选索引
