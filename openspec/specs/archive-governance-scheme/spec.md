# archive-governance-scheme Specification

## Purpose
TBD - created by archiving change add-archive-governance-foundation. Update Purpose after archive.
## Requirements
### Requirement: 治理方案与版本

系统 SHALL 使用档案治理方案和治理方案版本表达某个机构、客户或场景采用的一组档案本体、规则和后续分类、著录、档号配置。

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
- **THEN** 系统 SHALL 校验该版本引用的本体定义和规则定义均有效
- **AND** 系统 SHALL 将版本状态改为 `PUBLISHED`
- **AND** 系统 SHALL 记录发布时间和发布人

#### Scenario: 拒绝编辑已发布版本

- **WHEN** 客户端修改 `PUBLISHED`、`FROZEN` 或 `RETIRED` 治理方案版本的本体、规则或绑定配置
- **THEN** 系统 SHALL 拒绝修改
- **AND** 响应 SHALL 说明已发布或冻结版本不可原地修改

#### Scenario: 冻结已发布版本

- **WHEN** 客户端冻结 `PUBLISHED` 治理方案版本
- **THEN** 系统 SHALL 将版本状态改为 `FROZEN`
- **AND** `FROZEN` 版本 SHALL 继续用于解释历史档案
- **AND** `FROZEN` 版本 SHALL NOT 用于新建档案默认选择

#### Scenario: 退役冻结版本

- **WHEN** 客户端退役 `FROZEN` 治理方案版本
- **THEN** 系统 SHALL 将版本状态改为 `RETIRED`
- **AND** 系统 SHALL 保留该版本的历史解释能力
- **AND** 系统 SHALL NOT 删除已被档案记录引用的版本

### Requirement: 治理方案适用范围

治理方案版本 SHALL 明确适用范围，避免不同全宗、机构或业务场景误用同一规则集。

#### Scenario: 绑定适用范围

- **WHEN** 客户端为治理方案版本配置适用范围
- **THEN** 系统 SHALL 至少支持按全局默认、全宗编码和档案分类绑定适用范围
- **AND** 同一适用范围在同一时间 SHALL 只有一个默认 `PUBLISHED` 治理方案版本

#### Scenario: 解析档案默认治理方案

- **WHEN** 客户端创建档案条目或案卷且未显式提交治理方案版本
- **THEN** 系统 SHALL 按分类、全宗、全局默认的优先级解析治理方案版本
- **AND** 如果无法解析到 `PUBLISHED` 治理方案版本，系统 SHALL 拒绝创建

### Requirement: 历史档案治理版本

档案记录 SHALL 能追溯其创建或正式归档时采用的治理方案版本。

#### Scenario: 新建档案保存治理方案版本

- **WHEN** 客户端创建档案条目或案卷
- **THEN** 系统 SHALL 保存该记录采用的治理方案版本 ID
- **AND** 后续治理方案发布新版本 SHALL NOT 自动改写该记录的治理方案版本 ID

#### Scenario: 读取历史档案

- **WHEN** 客户端读取已保存治理方案版本 ID 的历史档案
- **THEN** 系统 SHALL 使用该版本解释本体属性、规则命中和展示含义
- **AND** 系统 SHALL NOT 使用最新版本重算历史档案的制度性含义

### Requirement: 治理方案绑定边界

治理方案版本 SHALL 只绑定配置和规则引用，不直接承载档案主数据。

#### Scenario: 保存治理方案绑定

- **WHEN** 客户端为治理方案版本绑定本体配置、规则集、分类方案引用、著录方案引用或档号规则引用
- **THEN** 系统 SHALL 保存引用关系
- **AND** 系统 SHALL NOT 将档案题名、文号、责任者、文件摘要等档案实例值保存到治理方案版本中

#### Scenario: 删除被引用配置

- **WHEN** 客户端删除已被 `PUBLISHED`、`FROZEN` 或 `RETIRED` 治理方案版本引用的本体定义或规则定义
- **THEN** 系统 SHALL 拒绝删除
- **AND** 响应 SHALL 说明该配置已被治理方案版本引用
