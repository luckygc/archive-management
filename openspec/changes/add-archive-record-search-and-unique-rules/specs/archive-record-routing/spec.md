## ADDED Requirements

### Requirement: 档案记录全宗路由
系统 SHALL 使用分类动态表保存档案记录所属全宗，统一档案记录主表不再保存全宗编码和全宗名称。

#### Scenario: 创建档案记录
- **WHEN** 客户端基于某个档案分类和全宗创建档案记录
- **THEN** 系统 SHALL 在统一档案记录主表保存分类、档号、状态、年度和业务锁字段
- **AND** 系统 SHALL 在该分类动态表保存同 ID 动态行
- **AND** 系统 SHALL 在分类动态表固定字段 `fonds_code` 中保存全宗编码
- **AND** 系统 SHALL NOT 在统一档案记录主表保存全宗编码或全宗名称

#### Scenario: 固定档案记录字段
- **WHEN** 客户端创建或编辑档案记录
- **THEN** 系统 SHALL 将档号、年度、档案状态、流程状态、密级、排序、归档时间和锁定信息作为统一档案记录主表字段处理
- **AND** 系统 SHALL NOT 将这些固定字段写入分类动态表
- **AND** 系统 SHALL NOT 要求这些固定字段存在于分类字段定义表

#### Scenario: 按全宗查询分类档案
- **WHEN** 客户端指定档案分类和全宗编码查询档案库
- **THEN** 系统 SHALL 通过该分类动态表的 `fonds_code` 字段筛选记录
- **AND** 系统 SHALL 按动态表 ID 回连统一档案记录主表读取记录状态和业务锁字段

### Requirement: 动态档案查询请求
系统 SHALL 支持以分类为核心的动态档案查询请求。

#### Scenario: 查询单分类档案
- **WHEN** 客户端指定档案分类查询档案库
- **THEN** 系统 SHALL 读取该分类字段定义和动态表名
- **AND** 系统 SHALL 返回主表通用字段、固定全宗字段和列表显示动态字段

#### Scenario: 查询未选择分类
- **WHEN** 客户端未指定档案分类查询档案库
- **THEN** 系统 SHALL NOT 跨多张分类动态表拼接不同动态字段
- **AND** 系统 SHALL 只返回统一档案记录主表通用概览或空记录列表

#### Scenario: 动态列为空
- **WHEN** 分类动态表查询结果中某个列表字段值为 `null`
- **THEN** 系统 SHALL 在返回的行数据中保留该动态列 key

### Requirement: 档案记录操作审计
系统 SHALL 为档案记录创建、删除、锁定、解锁等业务操作记录操作审计。

#### Scenario: 创建档案记录
- **WHEN** 客户端创建档案记录
- **THEN** 系统 SHALL 写入档案记录操作审计
- **AND** 审计 SHALL 记录操作类型、档案记录 ID、全宗编码、分类编码、操作人和操作时间
- **AND** 审计 SHALL NOT 保存记录数据快照

#### Scenario: 删除档案记录
- **WHEN** 客户端删除档案记录
- **THEN** 系统 SHALL 写入档案记录操作审计
- **AND** 审计操作类型 SHALL 为 `DELETE`
- **AND** 审计 SHALL 保存删除原因
- **AND** 审计 SHALL NOT 保存删除前记录快照

#### Scenario: 锁定或解锁档案记录
- **WHEN** 客户端锁定或解锁档案记录
- **THEN** 系统 SHALL 写入档案记录操作审计
- **AND** 审计 SHALL 记录操作类型、档案记录 ID、全宗编码、分类编码、操作人和操作时间
- **AND** 审计 SHALL NOT 保存操作前后记录快照
