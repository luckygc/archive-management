## ADDED Requirements

### Requirement: 用户定义运行时约束
系统 SHALL 允许管理员定义候选档案数据必须满足的结构化断言。

#### Scenario: 创建运行时约束
- **WHEN** 管理员提交治理版本、约束编码、名称、固定触发点、作用域、优先级、断言条件、失败处理和消息
- **THEN** 系统 SHALL 将约束保存为 `DRAFT`
- **AND** 失败处理 SHALL 只接受 `REJECT` 或 `WARN`
- **AND** 约束编码 SHALL 在同一治理版本内唯一

#### Scenario: 约束断言失败
- **WHEN** 运行时约束的断言对最终候选事实求值为假
- **THEN** `REJECT` 约束 SHALL 形成阻断决策
- **AND** `WARN` 约束 SHALL 形成非阻断警告决策

### Requirement: 用户定义运行时规则
系统 SHALL 允许管理员使用结构化 `WHEN` 条件组合系统提供的固定动作。

#### Scenario: 创建运行时规则
- **WHEN** 管理员提交治理版本、规则编码、名称、固定触发点、作用域、优先级、条件和固定动作列表
- **THEN** 系统 SHALL 将规则及动作保存为 `DRAFT`
- **AND** 系统 SHALL 拒绝空动作列表、未知动作或不兼容的动作参数
- **AND** 规则编码 SHALL 在同一治理版本内唯一

#### Scenario: 规则条件命中
- **WHEN** 已发布启用规则的 `WHEN` 条件对候选事实求值为真
- **THEN** 系统 SHALL 按动作顺序执行已注册固定动作
- **AND** 系统 SHALL NOT 执行规则配置提供的 SQL、脚本、类名、Bean 名或函数名

### Requirement: 固定运行时触发点
系统 SHALL 只在代码注册并接入真实业务 Service 的固定触发点执行用户定义配置。

#### Scenario: 支持首批触发点
- **WHEN** 管理员创建约束或规则
- **THEN** 系统 SHALL 支持 `ITEM_BEFORE_CREATE`、`ITEM_BEFORE_UPDATE`、`ITEM_BEFORE_DELETE`、`VOLUME_BEFORE_CREATE`、`VOLUME_BEFORE_ADD_ITEM`、`FILE_BEFORE_UPLOAD` 和 `EXPORT_BEFORE_CREATE`
- **AND** 系统 SHALL 拒绝未知触发点

#### Scenario: 导入复用写入触发点
- **WHEN** 档案导入逐行创建或修改条目
- **THEN** 系统 SHALL 分别执行 `ITEM_BEFORE_CREATE` 或 `ITEM_BEFORE_UPDATE`
- **AND** 系统 SHALL NOT 维护仅供导入绕过正常写入规则的独立触发点

### Requirement: 真实字段目录与结构化条件
运行时条件 SHALL 直接引用当前分类的真实字段目录并使用受控 AST。

#### Scenario: 查询触发点字段目录
- **WHEN** 管理端按治理版本、分类和触发点查询可用字段
- **THEN** 系统 SHALL 返回稳定字段代码、名称、数据类型、来源、可读性和可写性
- **AND** 字段 SHALL 来自系统固定字段、分类动态字段、实物字段或只读上下文字段
- **AND** 系统 SHALL NOT 要求字段先注册为本体或字段语义

#### Scenario: 保存结构化条件
- **WHEN** 管理员提交约束断言或规则条件
- **THEN** 系统 SHALL 只接受 `all`、`any`、`not`、字段比较和空值判断节点
- **AND** 系统 SHALL 按字段数据类型限制操作符和参数类型
- **AND** 系统 SHALL 校验 AST 深度、节点数量、集合参数数量和文本长度上限

#### Scenario: 字段引用运行时失效
- **WHEN** 已发布定义引用的字段被删除、停用、改型或不再属于当前分类
- **THEN** 系统 SHALL 将本次执行标记为配置失效并阻断业务状态变更
- **AND** 系统 SHALL NOT 跳过失效定义继续写入

### Requirement: 系统固定动作
系统 SHALL 通过代码注册固定动作、参数合同、适用触发点和可写字段边界。

#### Scenario: 执行阻断动作
- **WHEN** 命中规则执行 `REJECT`
- **THEN** 系统 SHALL 形成包含规则编码、消息和节点位置的阻断决策
- **AND** 调用业务事务 SHALL 在任何持久化副作用前失败

#### Scenario: 执行警告动作
- **WHEN** 命中规则执行 `WARN`
- **THEN** 系统 SHALL 形成非阻断警告并记录必要追踪
- **AND** 有响应体的业务命令 SHALL 在专用响应视图中返回警告摘要

#### Scenario: 执行候选字段赋值
- **WHEN** 创建或修改类前置触发点命中 `SET_FIELD`
- **THEN** 系统 SHALL 校验目标字段在当前字段目录中可写且参数可转换为目标类型
- **AND** 系统 SHALL 只修改当前事务尚未持久化的候选值
- **AND** 系统 SHALL NOT 由动作直接调用 Repository、Mapper、流程、事件、远程服务或文件系统

#### Scenario: 拒绝不兼容动作
- **WHEN** 管理员在删除、文件上传或导出触发点配置 `SET_FIELD`
- **THEN** 系统 SHALL 拒绝保存或发布该规则
- **AND** 响应 SHALL 定位不兼容的触发点和动作

### Requirement: 发布校验与不可变性
运行时定义发布前 SHALL 完成结构、字段、动作、作用域和数据库状态校验，发布后定义语义 SHALL 不可原地修改。

#### Scenario: 发布有效定义
- **WHEN** 管理员发布通过全部校验的草稿约束或规则
- **THEN** 系统 SHALL 将状态改为 `PUBLISHED`
- **AND** 系统 SHALL 记录发布人、发布时间和字段目录签名

#### Scenario: 拒绝发布无效定义
- **WHEN** 草稿存在缺失字段、类型不兼容操作符、未知动作、冲突参数、未知触发点或不可编辑治理版本
- **THEN** 系统 SHALL 拒绝发布
- **AND** 响应 SHALL 返回可定位到定义、条件节点、动作或字段的错误

#### Scenario: 修改已发布定义
- **WHEN** 客户端或直接数据库写入尝试修改或删除已发布定义及其动作
- **THEN** 系统 SHALL 拒绝操作
- **AND** 系统 SHALL 只允许通过受审计的启用或停用动作改变运行态开关

### Requirement: 确定性事务内执行
系统 SHALL 在业务持久化前以单次、稳定顺序执行运行时规则和约束。

#### Scenario: 执行运行时配置
- **WHEN** 业务 Service 到达固定触发点
- **THEN** 系统 SHALL 按治理版本、触发点和作用域加载已发布启用定义
- **AND** 规则 SHALL 按 `priority ASC, definitionCode ASC, id ASC` 执行并修改候选事实
- **AND** 约束 SHALL 在规则动作完成后对最终候选事实执行
- **AND** 系统 SHALL NOT 因字段赋值从头循环执行规则

#### Scenario: 多规则字段赋值冲突
- **WHEN** 两个命中规则为同一字段设置不同候选值
- **THEN** 系统 SHALL 形成配置冲突并阻断事务
- **AND** 系统 SHALL 返回冲突字段和规则编码
- **AND** 相同值的重复赋值 SHALL 视为幂等

#### Scenario: 阻断决策回滚业务事务
- **WHEN** 执行结果包含 `REJECT`、失效配置、动作冲突、执行超限或执行器错误
- **THEN** 业务 Service SHALL 不提交主表、动态表、文件元数据、审计或规则追踪的部分写入
- **AND** HTTP 边界 SHALL 返回项目 ProblemDetail 和稳定错误码

### Requirement: 试运行与决策追踪
系统 SHALL 提供无副作用试运行和受权限、数据范围保护的运行时决策追踪。

#### Scenario: 试运行配置
- **WHEN** 管理员提交治理版本、固定触发点、作用域和候选事实执行试运行
- **THEN** 系统 SHALL 使用与真实运行相同的字段解析、排序、条件和动作核心
- **AND** 响应 SHALL 返回执行顺序、命中结果、候选字段变化、警告、阻断和跳过原因
- **AND** 试运行 SHALL NOT 写入档案主数据、动态表、文件、审计或追踪

#### Scenario: 记录运行时决策
- **WHEN** 真实业务操作执行运行时配置且事务成功
- **THEN** 系统 SHALL 保存治理版本、触发点、对象类型、对象 ID、定义编码、命中状态、动作摘要、结果和执行时间
- **AND** 追踪 SHALL NOT 保存未脱敏的敏感字段全文

#### Scenario: 查询运行时决策
- **WHEN** 用户查询运行时决策追踪
- **THEN** 系统 SHALL 在数据库中先应用功能权限和档案数据范围
- **AND** 系统 SHALL 按 `created_at DESC, id DESC` 使用项目统一 cursor 稳定翻页

## REMOVED Requirements

### Requirement: 本地规则定义
**Reason**: 原模型混合九类规则和自由 effect，但没有固定业务触发合同。

**Migration**: 当前 `0.0.1` 不迁移旧规则数据；删除旧定义并使用新的运行时约束和规则重新配置。

### Requirement: 规则作用域
**Reason**: 原作用域包含任意字符串触发点和遗留对象代码。

**Migration**: 作用域改为治理版本、全宗、分类、档案层级和系统固定触发点。

### Requirement: 结构化条件树
**Reason**: 条件树合同需要与真实字段目录、资源上限和直接字段引用一起重建。

**Migration**: 不保留旧 AST 数据；新定义直接引用真实字段代码。

### Requirement: 规则事实解析
**Reason**: 原解析依赖字段语义注册并包含未接入业务的数据来源。

**Migration**: 使用固定字段、分类动态字段、实物字段和上下文字段组成的实时字段目录。

### Requirement: 规则 effect
**Reason**: 泛化 effect 宣称了尚未实现且可能产生副作用的能力。

**Migration**: 仅保留代码实现并接入事务边界的 `REJECT`、`WARN`、`SET_FIELD` 固定动作。

### Requirement: 规则发布校验
**Reason**: 原发布校验未校验固定触发点、动作处理器兼容性和真实字段可写性。

**Migration**: 使用新的运行时定义发布校验和数据库不可变边界。

### Requirement: 规则执行决策
**Reason**: 原执行主要由手工 API 驱动，调用方可选择是否处理阻断结果。

**Migration**: 真实业务 Service 在持久化前强制执行并统一处理阻断、警告和候选字段变化。

### Requirement: 规则决策追踪
**Reason**: 追踪字段和语义需要随新定义、触发点和固定动作模型重建。

**Migration**: 删除旧追踪结构与数据；新追踪只使用运行时定义编码和固定动作摘要。
