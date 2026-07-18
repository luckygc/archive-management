# archive-runtime-check-portability Specification

## Purpose

定义动态运行时配置的版本化快照、稳定编码和完整性摘要，以及跨环境完整预检、原子导入和草稿全量恢复合同。该能力只迁移用户定义约束、规则、作用域和固定动作参数，不包含档案业务数据、文件、用户、审计、追踪或整库灾备。

## Requirements

### Requirement: 运行时配置快照导出

系统 SHALL 导出不依赖数据库 ID、可校验的运行时配置快照。

#### Scenario: 导出配置快照

- **WHEN** 管理员导出一个治理版本的运行时配置
- **THEN** 快照 SHALL 包含格式版本、来源应用版本、治理稳定编码、作用域、定义、动作和字段引用
- **AND** 分类和字段 SHALL 使用稳定编码
- **AND** 快照 SHALL 包含规范化内容的 SHA-256 摘要
- **AND** 快照 SHALL NOT 包含档案、文件、用户、权限、审计、追踪或数据库 ID

### Requirement: 配置快照完整预检

系统 SHALL 在导入或恢复前完整预检配置快照和目标字段目录。

#### Scenario: 预检快照

- **WHEN** 管理员提交摘要正确且格式受支持的快照
- **THEN** 系统 SHALL 按治理、分类和字段稳定编码解析引用
- **AND** 系统 SHALL 校验字段类型、读写能力、触发点和动作参数兼容性
- **AND** 系统 SHALL 返回定义数量、作用域数量、字段映射和摘要

#### Scenario: 拒绝无效快照

- **WHEN** 快照被篡改、超限、引用缺失或类型不兼容
- **THEN** 系统 SHALL 拒绝预检并定位定义、字段和原因
- **AND** 系统 SHALL NOT 执行包内 SQL、脚本或类名

### Requirement: 配置快照原子导入

系统 SHALL 将通过预检的快照原子导入为新的草稿治理版本。

#### Scenario: 导入新草稿

- **WHEN** 管理员确认导入
- **THEN** 系统 SHALL 在单个事务中创建新的 `DRAFT` 治理版本、作用域、约束、规则和动作
- **AND** 系统 SHALL NOT 覆盖现有版本或自动发布配置

#### Scenario: 导入中途失败

- **WHEN** 任一步骤失败
- **THEN** 系统 SHALL 回滚本次导入的全部写入
- **AND** 目标环境原有治理配置 SHALL 保持不变

### Requirement: 草稿运行时配置快速恢复

系统 SHALL 对管理员明确选择的草稿版本执行原子全量恢复。

#### Scenario: 恢复草稿

- **WHEN** 管理员选择通过预检的快照和 `DRAFT` 目标
- **THEN** 系统 SHALL 在单个事务中全量替换目标的运行时定义和动作
- **AND** 响应 SHALL 返回恢复前后数量、字段映射和快照摘要

#### Scenario: 恢复失败或目标非草稿

- **WHEN** 恢复任一步骤失败或目标不是 `DRAFT`
- **THEN** 系统 SHALL 回滚并保持目标原内容
- **AND** 系统 SHALL 拒绝覆盖已发布、冻结或退役版本
