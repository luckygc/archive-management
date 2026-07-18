# archive-local-rule-engine Specification

## Purpose

定义用户运行时约束和规则的真实字段目录、结构化条件、固定触发点、固定动作、发布边界、事务执行、试运行和决策追踪合同。

## Requirements

### Requirement: 用户定义运行时约束和规则

系统 SHALL 允许管理员在治理版本下定义结构化约束和规则。

#### Scenario: 创建运行时约束

- **WHEN** 管理员提交编码、名称、固定触发点、作用域、优先级、断言、失败处理和消息
- **THEN** 系统 SHALL 保存 `DRAFT` 约束
- **AND** 失败处理 SHALL 只接受 `REJECT` 或 `WARN`
- **AND** 编码 SHALL 在同一治理版本内唯一

#### Scenario: 创建运行时规则

- **WHEN** 管理员提交固定触发点、结构化条件和固定动作列表
- **THEN** 系统 SHALL 保存 `DRAFT` 规则及其动作
- **AND** 系统 SHALL 拒绝空动作列表、未知动作或不兼容参数
- **AND** 系统 SHALL NOT 执行配置提供的 SQL、脚本、类名、Bean 名或函数名

### Requirement: 固定运行时触发点

系统 SHALL 只在代码注册并接入真实业务 Service 的固定触发点执行用户配置。

#### Scenario: 支持首批触发点

- **WHEN** 管理员创建约束或规则
- **THEN** 系统 SHALL 支持 `ITEM_BEFORE_CREATE`、`ITEM_BEFORE_UPDATE`、`ITEM_BEFORE_DELETE`、`VOLUME_BEFORE_CREATE`、`VOLUME_BEFORE_ADD_ITEM`、`FILE_BEFORE_UPLOAD` 和 `EXPORT_BEFORE_CREATE`
- **AND** 系统 SHALL 拒绝未知触发点

#### Scenario: 导入复用写入触发点

- **WHEN** 导入逐行创建或修改条目
- **THEN** 系统 SHALL 分别执行 `ITEM_BEFORE_CREATE` 或 `ITEM_BEFORE_UPDATE`
- **AND** 导入 SHALL NOT 通过独立触发点绕过正常写入合同

### Requirement: 真实字段目录与结构化条件

运行时条件 SHALL 直接引用当前分类和触发点的真实字段目录并使用受控 AST。

#### Scenario: 查询字段目录

- **WHEN** 管理端按治理版本、分类和触发点查询字段
- **THEN** 系统 SHALL 返回稳定字段代码、名称、数据类型、来源、可读性和可写性
- **AND** 字段 SHALL 来自系统固定字段、分类动态字段、实物字段或只读上下文字段

#### Scenario: 保存结构化条件

- **WHEN** 管理员提交约束断言或规则条件
- **THEN** 系统 SHALL 只接受 `all`、`any`、`not`、类型化比较和空值判断节点
- **AND** 系统 SHALL 按字段类型限制操作符和参数类型
- **AND** 系统 SHALL 校验 AST 深度、节点数、集合参数数和文本长度上限

#### Scenario: 已发布字段引用失效

- **WHEN** 已发布定义引用的字段被删除、停用、改型或移出当前分类
- **THEN** 系统 SHALL 阻止破坏性字段变更或在执行时失败关闭
- **AND** 系统 SHALL NOT 跳过失效定义继续写入

### Requirement: 系统固定动作

系统 SHALL 通过代码注册动作、参数合同、适用触发点和可写字段边界。

#### Scenario: 执行固定动作

- **WHEN** 规则命中 `REJECT`、`WARN` 或 `SET_FIELD`
- **THEN** `REJECT` SHALL 形成阻断决策
- **AND** `WARN` SHALL 形成非阻断警告
- **AND** `SET_FIELD` SHALL 只修改当前事务尚未持久化且类型兼容的可写候选字段
- **AND** 动作处理器 SHALL NOT 直接调用持久化、流程、事件、远程服务或文件系统

#### Scenario: 拒绝不兼容动作

- **WHEN** 管理员在删除、文件上传或导出触发点配置 `SET_FIELD`
- **THEN** 系统 SHALL 拒绝保存或发布
- **AND** 响应 SHALL 定位触发点和动作

### Requirement: 发布校验与不可变性

运行时定义发布前 SHALL 完成结构、字段、动作、作用域和数据库状态校验，发布后语义不可原地修改。

#### Scenario: 发布有效定义

- **WHEN** 管理员发布通过校验的草稿定义
- **THEN** 系统 SHALL 将状态改为 `PUBLISHED`
- **AND** 系统 SHALL 记录发布人、发布时间和字段目录签名

#### Scenario: 修改已发布定义

- **WHEN** 客户端或直接 SQL 尝试修改或删除已发布定义及其动作
- **THEN** 数据库和应用服务 SHALL 拒绝操作
- **AND** 系统 SHALL 只允许受审计的启用或停用动作改变运行态开关

### Requirement: 确定性事务内执行

系统 SHALL 在业务持久化前以单次稳定顺序执行运行时规则和约束。

#### Scenario: 执行运行时配置

- **WHEN** 业务 Service 到达固定触发点
- **THEN** 系统 SHALL 按治理版本、触发点和作用域加载已发布启用定义
- **AND** 规则 SHALL 按 `priority ASC, definition_code ASC, id ASC` 执行并修改候选事实
- **AND** 约束 SHALL 在规则完成后检查最终候选事实
- **AND** 系统 SHALL NOT 因字段赋值循环执行规则

#### Scenario: 字段赋值冲突

- **WHEN** 两个命中规则为同一字段设置不同值
- **THEN** 系统 SHALL 返回冲突字段和规则编码并阻断事务
- **AND** 相同值的重复赋值 SHALL 视为幂等

#### Scenario: 阻断时零部分写入

- **WHEN** 结果包含阻断、配置失效、动作冲突、执行超限或执行器错误
- **THEN** 业务事务 SHALL 不提交主数据、动态表、文件元数据、审计或决策追踪的部分写入
- **AND** HTTP 边界 SHALL 返回项目 ProblemDetail 和稳定错误码

### Requirement: 试运行与决策追踪

系统 SHALL 提供无副作用试运行和受权限、数据范围保护的运行时决策追踪。

#### Scenario: 试运行配置

- **WHEN** 管理员提交治理版本、固定触发点、作用域和候选事实试运行
- **THEN** 系统 SHALL 复用真实字段解析、排序、条件和动作核心
- **AND** 响应 SHALL 返回命中结果、候选变化、警告和阻断
- **AND** 试运行 SHALL NOT 写主数据、文件、审计或追踪

#### Scenario: 记录和查询决策

- **WHEN** 真实业务操作成功执行运行时配置
- **THEN** 系统 SHALL 保存治理版本、触发点、对象、定义编码、动作摘要、结果和时间
- **AND** 追踪 SHALL NOT 保存未脱敏的敏感字段全文
- **AND** 查询 SHALL 先在数据库中应用功能权限和档案数据范围，再按 `created_at DESC, id DESC` 使用统一 cursor 分页
