## ADDED Requirements

### Requirement: 本地规则定义

系统 SHALL 使用本地规则定义表达治理方案版本下的校验、派生、档号、保管、访问、检测、移交、归档和导出规则。

#### Scenario: 创建本地规则

- **WHEN** 客户端提交规则编码、名称、规则类型、触发点、作用域、优先级、条件树、effect 和启停状态
- **THEN** 系统 SHALL 保存本地规则
- **AND** 规则编码 SHALL 在同一治理方案版本内唯一
- **AND** 规则类型 SHALL 使用受控枚举表达
- **AND** 规则初始状态 SHALL 为 `DRAFT`

#### Scenario: 支持规则类型

- **WHEN** 客户端创建本地规则
- **THEN** 系统 SHALL 至少支持 `VALIDATION`、`DERIVATION`、`REFERENCE_CODE`、`RETENTION`、`ACCESS`、`QUALITY`、`TRANSFER`、`FILING` 和 `EXPORT` 规则类型
- **AND** 系统 SHALL 拒绝未知规则类型

### Requirement: 规则作用域

本地规则 SHALL 明确作用域，避免跨全宗、分类、层级或对象误用。

#### Scenario: 配置规则作用域

- **WHEN** 客户端为规则配置作用域
- **THEN** 系统 SHALL 支持按治理方案版本、全宗编码、档案分类、对象类型、对象层级和触发事件限定作用域
- **AND** 系统 SHALL 校验作用域引用的本体对象类型、事件类型和分类存在且启用

#### Scenario: 解析适用规则

- **WHEN** 业务用例请求执行某个触发点的规则
- **THEN** 系统 SHALL 按治理方案版本、触发点、作用域和启停状态筛选规则
- **AND** 系统 SHALL 按优先级和规则编码形成稳定执行顺序

### Requirement: 结构化条件树

本地规则条件 SHALL 使用结构化 AST 表达，禁止自由脚本和自由 SQL。

#### Scenario: 保存条件树

- **WHEN** 客户端提交规则条件树
- **THEN** 系统 SHALL 只接受 `all`、`any`、`not` 和字段条件节点
- **AND** 字段条件节点 SHALL 包含字段引用、操作符和参数值
- **AND** 系统 SHALL 校验条件树深度和节点数量不超过系统限制

#### Scenario: 拒绝自由表达式

- **WHEN** 客户端在规则条件中提交 SQL、SpEL、MVEL、JavaScript、Groovy 或其他自由表达式
- **THEN** 系统 SHALL 拒绝保存
- **AND** 响应 SHALL 说明本地规则只支持结构化条件树

### Requirement: 规则事实解析

本地规则 SHALL 通过事实解析器读取受控字段，不直接访问数据库表、SQL 或 HTTP 上下文。

#### Scenario: 解析字段引用

- **WHEN** 系统发布或执行规则
- **THEN** 系统 SHALL 将字段引用解析为固定字段、动态字段、明细字段、文件组件字段、关系字段、事件字段或上下文字段
- **AND** 系统 SHALL 校验字段引用在当前治理方案版本和作用域内可见
- **AND** 系统 SHALL 拒绝无法解析的字段引用

#### Scenario: 限制操作符

- **WHEN** 系统校验字段条件节点
- **THEN** 系统 SHALL 按字段数据类型限制可用操作符
- **AND** 文本、数字、日期、枚举、布尔、引用和集合类型 SHALL 使用各自的操作符白名单
- **AND** 系统 SHALL 拒绝字段类型不支持的操作符

### Requirement: 规则 effect

本地规则执行 SHALL 只产出受控 effect，不直接执行副作用。

#### Scenario: 保存规则 effect

- **WHEN** 客户端提交规则 effect
- **THEN** 系统 SHALL 只接受白名单 effect
- **AND** effect 参数 SHALL 通过结构化字段表达
- **AND** 系统 SHALL 校验 effect 与规则类型和触发点兼容

#### Scenario: 支持 effect 类型

- **WHEN** 系统执行规则
- **THEN** 系统 SHALL 至少支持 `VALIDATION_ERROR`、`WARNING`、`SUGGEST_VALUE`、`DERIVED_VALUE`、`REQUIRE_REVIEW`、`REQUIRE_QUALITY_CHECK`、`DENY_ACCESS`、`MASK_FIELD` 和 `INCLUDE_IN_PACKAGE`
- **AND** 系统 SHALL 拒绝未知 effect 类型

#### Scenario: 规则引擎不执行副作用

- **WHEN** 规则命中并产生 effect
- **THEN** 规则引擎 SHALL 返回规则决策
- **AND** 规则引擎 SHALL NOT 直接写数据库
- **AND** 规则引擎 SHALL NOT 直接发布业务事件
- **AND** 规则引擎 SHALL NOT 直接启动流程实例

### Requirement: 规则发布校验

本地规则发布前 SHALL 完成结构、引用和安全校验。

#### Scenario: 发布规则

- **WHEN** 客户端发布 `DRAFT` 规则
- **THEN** 系统 SHALL 校验规则类型、触发点、作用域、条件树、字段引用、操作符和 effect
- **AND** 系统 SHALL 将校验通过的规则状态改为 `PUBLISHED`
- **AND** 系统 SHALL 记录发布时间和发布人

#### Scenario: 拒绝发布无效规则

- **WHEN** 规则引用不存在字段、停用本体定义、未知操作符或未知 effect
- **THEN** 系统 SHALL 拒绝发布
- **AND** 响应 SHALL 返回可定位到规则节点的错误信息

### Requirement: 规则执行决策

本地规则执行 SHALL 返回可解释的规则决策。

#### Scenario: 执行规则

- **WHEN** 业务用例提交治理方案版本、触发点和事实上下文执行规则
- **THEN** 系统 SHALL 返回规则决策列表
- **AND** 每个决策 SHALL 包含规则 ID、规则编码、规则类型、命中状态、effect、消息和严重级别
- **AND** 未命中规则 SHALL 可在调试或审计模式下返回跳过原因

#### Scenario: 阻断规则命中

- **WHEN** 规则产生 `VALIDATION_ERROR` 或 `DENY_ACCESS` effect
- **THEN** 规则决策 SHALL 标记为阻断
- **AND** 调用方 SHALL 根据当前用例决定事务回滚、错误响应或人工处理

### Requirement: 规则决策追踪

系统 SHALL 保留必要的规则执行追踪，用于排查档号冲突、移交检测、利用拒绝、开放鉴定和四性检测问题。

#### Scenario: 记录规则追踪

- **WHEN** 业务用例要求记录规则追踪
- **THEN** 系统 SHALL 保存治理方案版本、触发点、对象类型、对象 ID、命中规则、effect、执行结果和执行时间
- **AND** 系统 SHALL NOT 在追踪记录中保存未脱敏的敏感字段全文

#### Scenario: 查询规则追踪

- **WHEN** 客户端查询某个档案对象或过程对象的规则追踪
- **THEN** 系统 SHALL 返回规则执行摘要
- **AND** 系统 SHALL 按当前用户功能权限和数据范围过滤可见追踪记录
