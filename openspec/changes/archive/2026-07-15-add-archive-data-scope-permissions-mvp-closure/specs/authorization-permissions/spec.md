## ADDED Requirements

### Requirement: 功能权限点枚举

系统 SHALL 提供项目内稳定功能权限点枚举，并将功能权限与数据范围分开表达。

#### Scenario: 枚举权限点

- **WHEN** 已认证管理员请求功能权限点列表
- **THEN** 系统 SHALL 返回权限编码、权限名称、所属模块和说明
- **AND** 权限编码 SHALL 使用稳定字符串，例如 `archive:item:read`、`archive:item:create`、`archive:item:update`、`archive:item:download-electronic-file`、`archive:export`、`archive:metadata:manage`、`authorization:role:manage`、`authentication:user:manage`
- **AND** 响应 SHALL NOT 包含数据范围条件

#### Scenario: 当前用户查询自身权限

- **WHEN** 已认证用户请求当前主体权限
- **THEN** 系统 SHALL 返回该用户通过启用角色获得的功能权限编码集合和是否为超级管理员
- **AND** 系统 SHALL NOT 因用户绑定了数据范围而额外授予功能权限

#### Scenario: 超级管理员拥有任意权限

- **WHEN** 用户拥有启用的内置 `超级管理员` 角色
- **THEN** 系统 SHALL 在服务端权限判断中提前放行任意功能权限
- **AND** 当前用户权限接口 SHALL 返回项目稳定权限点集合和 `superAdmin=true`

### Requirement: 角色功能权限绑定

系统 SHALL 支持角色绑定功能权限点，并按启用角色计算用户功能权限。

#### Scenario: 角色绑定权限点

- **WHEN** 管理员为角色保存功能权限点集合
- **THEN** 系统 SHALL 校验每个权限点存在
- **AND** 系统 SHALL 覆盖该角色已有权限点绑定
- **AND** 系统 SHALL NOT 接受未知权限编码

#### Scenario: 角色禁用后权限不生效

- **WHEN** 用户拥有一个已禁用角色
- **THEN** 系统 SHALL NOT 将该角色绑定的功能权限计入当前用户权限

### Requirement: 角色管理

系统 SHALL 支持管理员维护授权角色，并保护内置超级管理员角色不被破坏。

#### Scenario: 创建和查询角色

- **WHEN** 拥有角色管理权限的管理员创建或查询角色
- **THEN** 系统 SHALL 使用 `authorization-roles` 资源表达角色
- **AND** 列表接口 SHALL 使用项目统一 cursor 分页
- **AND** 创建角色时系统 SHALL 校验角色名称唯一且默认启用

#### Scenario: 更新角色

- **WHEN** 拥有角色管理权限的管理员更新普通角色
- **THEN** 系统 SHALL 支持修改角色名称、说明和启用状态
- **AND** 系统 SHALL 校验角色名称唯一

#### Scenario: 保护超级管理员角色

- **WHEN** 管理员尝试重命名、停用或删除内置 `超级管理员` 角色
- **THEN** 系统 SHALL 拒绝操作
- **AND** 系统 SHALL 保留该角色的超级管理员语义

### Requirement: 用户管理

系统 SHALL 支持管理员维护本地认证用户、重置密码并绑定用户角色。

#### Scenario: 创建和查询本地用户

- **WHEN** 拥有用户管理权限的管理员创建或查询本地认证用户
- **THEN** 系统 SHALL 使用 `authentication-users` 资源表达用户
- **AND** 列表接口 SHALL 使用项目统一 cursor 分页
- **AND** 创建用户时系统 SHALL 校验登录名唯一并对密码进行哈希存储

#### Scenario: 更新用户和重置密码

- **WHEN** 拥有用户管理权限的管理员更新用户资料、启停用户或重置密码
- **THEN** 系统 SHALL 校验目标用户存在
- **AND** 重置密码 SHALL 使用 `POST /api/v1/authentication-users/{id}:resetPassword`
- **AND** 新密码 SHALL 只以哈希结果写入本地用户表

#### Scenario: 绑定用户角色

- **WHEN** 管理员保存用户角色集合
- **THEN** 系统 SHALL 校验每个角色存在且启用
- **AND** 系统 SHALL 覆盖该用户已有角色绑定
- **AND** 前端在加载用户当前角色失败时 SHALL 禁止以空角色集合覆盖保存

### Requirement: 后端功能权限判断

系统 SHALL 在业务服务端判断功能权限，不能只依赖前端按钮隐藏。

#### Scenario: 缺少功能权限访问受保护能力

- **WHEN** 已认证用户请求需要功能权限的业务接口且不具备对应权限
- **THEN** 系统 SHALL 拒绝请求
- **AND** 响应 SHALL 使用项目统一 ProblemDetail 错误模型表达权限不足

#### Scenario: 具备功能权限但无数据范围

- **WHEN** 已认证用户具备档案操作功能权限但没有命中任何档案数据范围
- **THEN** 系统 SHALL 拒绝读取或写入档案数据
- **AND** 系统 SHALL NOT 将功能权限视为 `*` 数据范围
