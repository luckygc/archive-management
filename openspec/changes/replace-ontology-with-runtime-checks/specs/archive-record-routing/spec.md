## ADDED Requirements

### Requirement: 条目运行时规则触发
档案条目状态变更 SHALL 在同一业务事务中执行对应治理版本的用户定义运行时规则和约束。

#### Scenario: 创建条目前执行运行时配置
- **WHEN** 客户端创建档案条目且基础字段、分类字段和权限校验通过
- **THEN** 系统 SHALL 在写入条目主表和动态表前执行 `ITEM_BEFORE_CREATE`
- **AND** `SET_FIELD` 产生的最终候选值 SHALL 用于后续数据范围校验和数据库写入
- **AND** 阻断决策 SHALL 使条目、动态字段、审计和追踪均不产生部分写入

#### Scenario: 修改条目前执行运行时配置
- **WHEN** 客户端修改未锁定档案条目
- **THEN** 系统 SHALL 将当前完整记录与请求变更合并为候选事实并执行 `ITEM_BEFORE_UPDATE`
- **AND** 未提交字段 SHALL 使用当前值参与条件和约束判断
- **AND** 最终候选值 SHALL 再次通过类型、数据范围和数据库约束校验后写入

#### Scenario: 删除条目前执行运行时配置
- **WHEN** 客户端删除未锁定档案条目
- **THEN** 系统 SHALL 在删除标记、动态行、审计和搜索投影发生变化前执行 `ITEM_BEFORE_DELETE`
- **AND** 阻断决策 SHALL 保持条目及关联投影不变

#### Scenario: 条目命令产生非阻断警告
- **WHEN** 条目创建或修改成功且运行时执行产生 `WARN`
- **THEN** 对应命令响应 SHALL 返回警告编码和可展示消息
- **AND** 警告 SHALL 同时进入受权限保护的运行时决策追踪

### Requirement: 案卷运行时规则触发
案卷创建和条目入卷 SHALL 在持久化前执行固定触发点。

#### Scenario: 创建案卷前执行运行时配置
- **WHEN** 客户端在启用案卷管理的分类中创建案卷
- **THEN** 系统 SHALL 在案卷主表和动态表写入前执行 `VOLUME_BEFORE_CREATE`
- **AND** 阻断或配置错误 SHALL 回滚案卷创建事务

#### Scenario: 条目加入案卷前执行运行时配置
- **WHEN** 客户端把条目加入同分类同全宗的案卷
- **THEN** 系统 SHALL 在成员关系变化前执行 `VOLUME_BEFORE_ADD_ITEM`
- **AND** 候选事实 SHALL 同时包含案卷和条目的受控可读字段
- **AND** 阻断决策 SHALL 保持条目原案卷归属和排序不变

### Requirement: 电子文件上传运行时规则触发
电子文件元数据落库前 SHALL 执行 `FILE_BEFORE_UPLOAD`。

#### Scenario: 上传电子文件前执行运行时配置
- **WHEN** 用户上传文件且基础权限、大小和存储请求校验通过
- **THEN** 系统 SHALL 使用档案条目、文件名、内容类型和文件大小等受控事实执行 `FILE_BEFORE_UPLOAD`
- **AND** 阻断决策 SHALL 阻止文件元数据写入
- **AND** 已写入临时对象的失败路径 SHALL 按现有文件存储补偿边界清理或标记过期对象

### Requirement: 运行时配置错误响应
运行时规则或约束阻断 SHALL 使用项目统一 ProblemDetail。

#### Scenario: 返回运行时阻断错误
- **WHEN** 业务命令被用户定义约束、`REJECT`、字段失效、动作冲突或执行超限阻断
- **THEN** 响应 SHALL 包含稳定 `code`、`reason`、`traceId` 和定义决策摘要
- **AND** 字段级错误 SHALL 使用 `fieldViolations` 定位 API 字段或动态字段代码
- **AND** 响应 SHALL NOT 暴露规则执行栈、SQL、敏感候选字段全文或内部类名
