## ADDED Requirements

### Requirement: 分类唯一规则管理

系统 SHALL 支持按档案分类维护唯一规则。

#### Scenario: 创建唯一规则

- **WHEN** 客户端为某个档案分类提交唯一规则名称、编码和字段组合
- **THEN** 系统 SHALL 保存唯一规则
- **AND** 系统 SHALL 只允许选择该分类下未删除的动态字段
- **AND** 系统 SHALL 按客户端提交的字段顺序保存规则字段

#### Scenario: 禁用唯一规则

- **WHEN** 客户端禁用某条唯一规则
- **THEN** 系统 SHALL 保留规则定义
- **AND** 系统 SHALL 删除或停用该规则对应的动态表唯一索引

### Requirement: 动态唯一索引

系统 SHALL 通过分类动态表部分唯一索引执行唯一规则。

#### Scenario: 创建动态字段唯一索引

- **WHEN** 唯一规则包含一个或多个动态字段
- **THEN** 系统 SHALL 在该分类动态表上按规则字段顺序创建唯一索引
- **AND** 唯一索引 SHALL 使用 `where deleted_flag = false` 只约束未删除动态行

#### Scenario: 使用动态字段组合表达范围唯一

- **WHEN** 客户端需要部门内或其他业务范围内唯一
- **THEN** 系统 SHALL 允许客户端选择部门等范围字段和业务字段共同组成唯一规则
- **AND** 系统 SHALL NOT 提供全宗固定字段特殊唯一范围开关

#### Scenario: 空值唯一语义

- **WHEN** 唯一规则字段值为 `NULL`
- **THEN** 系统 SHALL 使用 PostgreSQL 默认唯一索引语义
- **AND** 系统 SHALL NOT 将多个 `NULL` 值视为互相冲突

### Requirement: 唯一冲突响应

系统 SHALL 在档案记录保存时返回明确的唯一冲突错误。

#### Scenario: 创建记录触发唯一冲突

- **WHEN** 客户端创建档案记录导致分类动态表唯一索引冲突
- **THEN** 系统 SHALL 拒绝保存
- **AND** 响应 SHALL 说明违反唯一规则

#### Scenario: 删除记录后释放唯一值

- **WHEN** 客户端删除一条占用唯一值的档案记录
- **THEN** 系统 SHALL 将分类动态表行 `deleted_flag` 标记为 `true`
- **AND** 系统 SHALL 允许后续记录重新使用该唯一值
