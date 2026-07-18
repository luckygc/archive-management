## MODIFIED Requirements

### Requirement: 治理方案与版本
系统 SHALL 使用档案治理方案和治理方案版本表达某个机构、客户或场景采用的一组运行时约束、规则以及分类、著录和档号配置。

#### Scenario: 创建治理方案
- **WHEN** 客户端提交治理方案编码、名称、适用说明和排序
- **THEN** 系统 SHALL 保存治理方案
- **AND** 治理方案编码 SHALL 在未删除治理方案中唯一
- **AND** 系统 SHALL 对编码执行小写 snake_case 或 kebab-case 兼容校验

#### Scenario: 创建治理方案版本
- **WHEN** 客户端为治理方案创建新版本
- **THEN** 系统 SHALL 保存版本号、状态、版本说明和创建时间
- **AND** 新版本初始状态 SHALL 为 `DRAFT`
- **AND** 同一治理方案下版本号 SHALL 唯一

### Requirement: 治理方案状态流转
治理方案版本 SHALL 通过明确状态控制编辑、发布、冻结和退役。

#### Scenario: 发布草稿版本
- **WHEN** 客户端发布 `DRAFT` 治理方案版本
- **THEN** 系统 SHALL 校验该版本的运行时约束、规则、字段引用、固定动作和其他配置绑定均有效
- **AND** 系统 SHALL 将版本状态改为 `PUBLISHED`
- **AND** 系统 SHALL 记录发布时间和发布人

#### Scenario: 拒绝编辑已发布版本
- **WHEN** 客户端修改 `PUBLISHED`、`FROZEN` 或 `RETIRED` 治理方案版本的运行时定义、动作、作用域或绑定配置
- **THEN** 系统 SHALL 拒绝修改
- **AND** 响应 SHALL 说明已发布或冻结版本不可原地修改

#### Scenario: 冻结已发布版本
- **WHEN** 客户端冻结 `PUBLISHED` 治理方案版本
- **THEN** 系统 SHALL 将版本状态改为 `FROZEN`
- **AND** `FROZEN` 版本 SHALL 继续用于解释历史档案及其运行时决策
- **AND** `FROZEN` 版本 SHALL NOT 用于新建档案默认选择

#### Scenario: 退役冻结版本
- **WHEN** 客户端退役 `FROZEN` 治理方案版本
- **THEN** 系统 SHALL 将版本状态改为 `RETIRED`
- **AND** 系统 SHALL 保留该版本的历史配置和决策解释能力
- **AND** 系统 SHALL NOT 删除已被档案记录引用的版本

### Requirement: 历史档案治理版本
档案记录 SHALL 能追溯其创建或正式归档时采用的治理方案版本。

#### Scenario: 新建档案保存治理方案版本
- **WHEN** 客户端创建档案条目或案卷
- **THEN** 系统 SHALL 保存该记录采用的治理方案版本 ID
- **AND** 后续治理方案发布新版本 SHALL NOT 自动改写该记录的治理方案版本 ID

#### Scenario: 读取历史档案
- **WHEN** 客户端读取已保存治理方案版本 ID 的历史档案
- **THEN** 系统 SHALL 使用该版本解释运行时约束、规则决策和配置含义
- **AND** 系统 SHALL NOT 使用最新版本重算历史档案的制度性含义

### Requirement: 治理方案绑定边界
治理方案版本 SHALL 直接拥有运行时约束和规则，并只通过装配绑定引用其他仍可执行的外部配置。

#### Scenario: 保存治理方案绑定
- **WHEN** 客户端为治理方案版本绑定分类方案引用、著录方案引用或档号规则引用
- **THEN** 系统 SHALL 保存引用关系
- **AND** 系统 SHALL NOT 通过通用绑定保存本体、字段语义或运行时规则集引用
- **AND** 系统 SHALL NOT 将档案题名、文号、责任者、文件摘要等档案实例值保存到治理方案版本中

#### Scenario: 删除被引用配置
- **WHEN** 客户端删除已被 `PUBLISHED`、`FROZEN` 或 `RETIRED` 治理方案版本引用的配置或运行时定义
- **THEN** 系统 SHALL 拒绝删除
- **AND** 响应 SHALL 说明该配置已被治理方案版本引用或属于不可变历史版本
