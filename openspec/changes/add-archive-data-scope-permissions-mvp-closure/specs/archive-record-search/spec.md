## MODIFIED Requirements

### Requirement: 管理查询

系统 SHALL 支持后台管理场景按分类、全宗、固定字段、动态字段和档案状态查询档案记录，并在后端应用当前用户档案数据范围。

#### Scenario: 按当前用户数据范围查询档案

- **WHEN** 用户查询档案管理列表
- **THEN** 系统 SHALL 使用 `deleted_flag=false AND 用户数据范围 AND 用户查询条件` 查询档案
- **AND** 系统 SHALL 返回命中范围和查询条件的档案记录
- **AND** 系统 SHALL NOT 返回用户数据范围外档案

#### Scenario: 数据范围与用户条件冲突

- **WHEN** 用户查询条件与用户数据范围没有交集
- **THEN** 系统 SHALL 返回空结果
- **AND** 系统 SHALL NOT 放宽用户数据范围以满足查询条件

### Requirement: 全文发现

系统 SHALL 支持在普通查档入口执行全文发现，并在后端应用当前用户档案数据范围。

#### Scenario: 全文发现应用数据范围

- **WHEN** 用户执行全文发现
- **THEN** 系统 SHALL 在全文候选结果上应用当前用户数据范围
- **AND** 系统 SHALL NOT 返回用户数据范围外档案

#### Scenario: 未认证用户全文发现

- **WHEN** 未认证用户请求全文发现
- **THEN** 系统 SHALL 拒绝请求
- **AND** 系统 SHALL NOT 返回任何档案记录

### Requirement: 档案详情读取

系统 SHALL 在读取档案详情时校验当前用户数据范围。

#### Scenario: 读取范围内档案详情

- **WHEN** 用户读取其数据范围内的档案详情
- **THEN** 系统 SHALL 返回档案固定字段、动态字段和布局信息

#### Scenario: 拒绝读取范围外档案详情

- **WHEN** 用户读取其数据范围外的档案详情
- **THEN** 系统 SHALL 拒绝请求
- **AND** 响应 SHALL 使用项目统一 ProblemDetail 错误模型
