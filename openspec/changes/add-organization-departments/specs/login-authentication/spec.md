## ADDED Requirements

### Requirement: 用户管理

认证用户管理 SHALL 支持用户所属部门。

#### Scenario: 创建或更新用户所属部门

- **WHEN** 管理员为用户设置所属部门
- **THEN** 系统 SHALL 校验部门存在且启用
- **AND** 用户可以没有所属部门

#### Scenario: 返回用户所属部门

- **WHEN** 客户端查询用户列表或详情
- **THEN** 响应 SHALL 包含 `departmentId`
- **AND** 响应 SHOULD 包含 `departmentCode` 和 `departmentName` 供界面展示
