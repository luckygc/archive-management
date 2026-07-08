## ADDED Requirements

### Requirement: 本体对象类型

系统 SHALL 使用对象类型定义档案治理中可被统一解释的领域对象。

#### Scenario: 创建对象类型

- **WHEN** 客户端提交对象类型编码、名称、说明、来源和启停状态
- **THEN** 系统 SHALL 保存对象类型
- **AND** 对象类型编码 SHALL 在未删除对象类型中唯一
- **AND** 系统 SHALL 支持 `FONDS`、`CLASSIFICATION_SCHEME`、`CLASSIFICATION_NODE`、`ARCHIVE_VOLUME`、`ARCHIVE_ITEM`、`ARCHIVE_LINE`、`FILE_COMPONENT`、`FILING_BATCH`、`TRANSFER_PACKAGE`、`QUALITY_CHECK`、`ACCESS_EVENT` 和 `CATALOG` 这些内置对象类型

#### Scenario: 拒绝删除被引用对象类型

- **WHEN** 客户端删除已被属性类型、关系类型、规则定义或治理方案版本引用的对象类型
- **THEN** 系统 SHALL 拒绝删除
- **AND** 响应 SHALL 说明该对象类型已被引用

### Requirement: 本体属性类型

系统 SHALL 使用属性类型解释固定字段、动态字段、明细字段、文件组件字段和过程字段的业务语义。

#### Scenario: 创建属性类型

- **WHEN** 客户端提交属性编码、名称、数据类型、适用对象类型、字段域、基数和启停状态
- **THEN** 系统 SHALL 保存属性类型
- **AND** 属性编码 SHALL 在未删除属性类型中唯一
- **AND** 数据类型 SHALL 使用受控枚举表达
- **AND** 字段域 SHALL 至少支持描述、结构、管理、技术、权限利用和保存元数据

#### Scenario: 配置属性检索语义

- **WHEN** 客户端为属性类型配置精确筛选、排序、著录、档号或规则事实可见性
- **THEN** 系统 SHALL 保存这些语义标记
- **AND** 系统 SHALL NOT 提供字段级全文投影开关
- **AND** 全文投影策略 SHALL 由搜索投影能力和字段域共同决定

### Requirement: 属性到物理字段映射

属性类型 SHALL 通过受控映射解释现有物理字段，不直接保存档案实例值。

#### Scenario: 映射固定字段

- **WHEN** 客户端将属性类型映射到档案固定字段
- **THEN** 系统 SHALL 校验目标字段属于受控固定字段集合
- **AND** 系统 SHALL 校验属性数据类型与固定字段类型兼容
- **AND** 系统 SHALL 保存属性类型与固定字段编码的映射

#### Scenario: 映射分类动态字段

- **WHEN** 客户端将属性类型映射到分类动态字段
- **THEN** 系统 SHALL 校验动态字段存在且未删除
- **AND** 系统 SHALL 校验动态字段所属对象层级与属性适用对象类型兼容
- **AND** 系统 SHALL 校验动态字段类型与属性数据类型兼容
- **AND** 系统 SHALL 保存属性类型、分类、对象层级、字段域和动态字段编码的映射

#### Scenario: 拒绝本体保存实例值

- **WHEN** 客户端尝试在属性类型或属性映射中提交档案题名、文号、责任者、金额、文件摘要等实例值
- **THEN** 系统 SHALL 拒绝保存
- **AND** 响应 SHALL 说明本体只定义语义和映射，不保存档案实例值

### Requirement: 本体关系类型

系统 SHALL 使用关系类型解释档案对象之间的业务关系，并区分层级从属和业务关联。

#### Scenario: 创建关系类型

- **WHEN** 客户端提交关系编码、名称、来源对象类型、目标对象类型、方向、基数和启停状态
- **THEN** 系统 SHALL 保存关系类型
- **AND** 关系类型编码 SHALL 在未删除关系类型中唯一
- **AND** 方向 SHALL 使用受控枚举表达单向、双向或层级从属

#### Scenario: 配置层级从属关系

- **WHEN** 客户端创建 `ARCHIVE_VOLUME` 到 `ARCHIVE_ITEM` 的包含关系类型
- **THEN** 系统 SHALL 将该关系解释为案卷包含卷内
- **AND** 系统 SHALL 使用现有 `volume_id` 表达 item 到 volume 的挂接
- **AND** 系统 SHALL NOT 要求创建额外父子关系行替代 `volume_id`

#### Scenario: 配置业务关联关系

- **WHEN** 客户端创建 `ARCHIVE_ITEM` 到 `ARCHIVE_ITEM` 的关联关系类型
- **THEN** 系统 SHALL 将该关系解释为平级业务关联
- **AND** 系统 SHALL 使用条目关联表保存关系实例
- **AND** 系统 SHALL NOT 将平级业务关联混入案卷和卷内父子层级

### Requirement: 本体事件类型

系统 SHALL 使用事件类型解释归档、移交、检测、利用、导出和处置等过程行为。

#### Scenario: 创建事件类型

- **WHEN** 客户端提交事件编码、名称、适用对象类型、说明和启停状态
- **THEN** 系统 SHALL 保存事件类型
- **AND** 事件类型编码 SHALL 在未删除事件类型中唯一
- **AND** 系统 SHALL 支持 `CREATED`、`UPDATED`、`FILED`、`TRANSFERRED`、`ACCESSED`、`DOWNLOADED`、`EXPORTED`、`CHECKED`、`APPRAISED` 和 `DISPOSED` 这些内置事件类型

#### Scenario: 事件类型用于规则事实

- **WHEN** 本地规则引用事件类型作为触发点或事实条件
- **THEN** 系统 SHALL 校验事件类型存在且启用
- **AND** 系统 SHALL 拒绝引用已删除或停用事件类型的规则发布

### Requirement: 本体版本约束

被治理方案版本引用的本体定义 SHALL 可追溯且不可破坏历史解释。

#### Scenario: 发布后冻结本体定义

- **WHEN** 本体对象类型、属性类型、关系类型或事件类型已被 `PUBLISHED`、`FROZEN` 或 `RETIRED` 治理方案版本引用
- **THEN** 系统 SHALL 拒绝原地修改其编码、数据类型、适用对象、字段域或关系端点
- **AND** 客户端 SHALL 通过创建新定义或新治理方案版本表达制度性变更

#### Scenario: 停用未发布本体定义

- **WHEN** 客户端停用未被已发布治理方案版本引用的本体定义
- **THEN** 系统 SHALL 保存停用状态
- **AND** 停用定义 SHALL NOT 出现在新规则和新治理方案版本的可选列表中
