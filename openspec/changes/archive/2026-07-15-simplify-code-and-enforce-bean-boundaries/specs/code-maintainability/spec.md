## ADDED Requirements

### Requirement: 单实现业务能力直接使用具体类
业务模块中的 Service、Manager 和领域协作能力如果只有一个实现，且不是框架声明式合同、稳定基础设施端口或已发生的多实现策略，系统 MUST 直接使用具体类，不得保留接口、工厂或适配层作为未来预留。

#### Scenario: 单实现 Service
- **WHEN** 业务模块中的 Service 只有一个项目内实现且不存在稳定端口要求
- **THEN** 调用方直接通过构造器依赖该具体 Service 类
- **AND** 代码库中不存在只被该实现类实现的对应业务接口

#### Scenario: 合理接口例外
- **WHEN** 类型属于 Jakarta Data Repository、MyBatis Mapper、稳定基础设施端口或已经存在多个项目内实现的策略合同
- **THEN** 系统允许保留接口
- **AND** 架构测试不会把该类型报告为单实现业务接口违规

### Requirement: Spring Bean 公开方法不得在本类内互调
同一 Spring Bean 的 public 方法 MUST NOT 调用本类另一个 public 方法，无论调用是否显式使用 `this`。

#### Scenario: 共享本类实现
- **WHEN** 同一 Spring Bean 的多个 public 用例需要复用同一段逻辑
- **THEN** 各 public 方法调用命名明确的 private 方法
- **AND** private 方法不依赖 Spring 代理语义

#### Scenario: 独立业务能力协作
- **WHEN** 被复用能力具有独立事务、权限或业务用例边界
- **THEN** 该能力由另一个具体 Spring Bean 的 public 方法提供
- **AND** 调用方通过构造器注入该 Bean

#### Scenario: 禁止代理绕行
- **WHEN** 开发者尝试通过 self 注入、`ApplicationContext` 查找本 Bean、暴露代理或手工代理调用本类 public 方法
- **THEN** 架构检查或代码审查 MUST 拒绝该实现

### Requirement: 架构规则提供可定位反馈
系统 MUST 通过自动化架构测试检查单实现业务接口和 Spring Bean public 自调用，并在失败信息中给出可直接定位的类型和方法。

#### Scenario: public 自调用违规
- **WHEN** Spring 组件的 public 方法调用同一所有者类的另一个 public 方法
- **THEN** 架构测试失败
- **AND** 失败信息包含调用类、调用方法和目标方法

#### Scenario: 单实现接口违规
- **WHEN** `module` 的 Service 或 Manager 接口只有一个项目内实现且不属于明确例外
- **THEN** 架构测试失败
- **AND** 失败信息包含接口和唯一实现类

### Requirement: 业务模块只有一个公开能力入口
同一业务能力 MUST 只有一个可供 Controller 或跨模块调用的公开 Service 入口，不得长期同时保留专项 Service 与仅做转发的总门面。

#### Scenario: 专项 Service 已存在
- **WHEN** 某项业务能力已由职责明确的专项 Service 实现
- **THEN** Controller 和跨模块调用方依赖该具体 Service
- **AND** 总门面中不再保留仅做转发的同义 public 方法

### Requirement: 固定实体用户审计字段具有唯一技术来源
通过 Jakarta Data 无状态 Repository 写入的固定实体，其 `created_by` 和 `updated_by` MUST 由已验证的 Hibernate 审计拦截器统一填充；业务 Service 不得重复手工赋值，除非该写入路径不经过拦截器或业务合同明确要求指定操作人。

#### Scenario: 无状态 Repository 写入
- **WHEN** 固定实体通过 Jakarta Data Repository 执行 insert 或 update
- **THEN** 审计拦截器从当前 Spring Security 上下文填充用户审计字段
- **AND** Service 不重复设置相同字段

#### Scenario: 审计例外路径
- **WHEN** 写入由 MyBatis 执行、记录业务操作人或经测试证明不经过审计拦截器
- **THEN** SQL 或调用方显式维护审计字段
- **AND** 例外原因可从代码位置和测试中识别

### Requirement: 前端共享代码按业务域组织
前端 API 和类型 MUST 按稳定业务域组织，不得由单一 `archive.ts` 同时汇集认证、授权、组织以及所有档案能力。

#### Scenario: 页面使用业务 API
- **WHEN** 页面调用认证、授权、组织、元数据、档案记录、治理或规则 API
- **THEN** 页面从对应业务域 API 文件导入
- **AND** 该文件不暴露其他无关业务域的接口

#### Scenario: 避免额外抽象层
- **WHEN** 拆分前端 API 和类型
- **THEN** 系统继续直接使用现有 HTTP client 和语义化函数
- **AND** 不新增 Endpoint Factory、前端 Repository、通用 CRUD API 类或额外状态层

### Requirement: 优化不得改变外部业务合同
代码精简和边界治理 MUST 保持现有 HTTP API、数据库结构、权限判断、ProblemDetail 错误和前端业务行为不变，除非先修改对应业务 OpenSpec。

#### Scenario: 内部重构
- **WHEN** Service、前端文件或审计实现发生内部调整
- **THEN** 现有 API 路径、请求响应字段、状态码和权限结果保持不变
- **AND** 相关行为测试继续通过
