## ADDED Requirements

### Requirement: 全局分类树

系统 SHALL 使用单棵全局分类树组织档案分类，不提供分类方案、分类分组或默认分类资源。

#### Scenario: 创建顶级分类

- **WHEN** 管理员创建不带父级的档案分类
- **THEN** 系统 SHALL 将其保存为全局分类树的顶级节点
- **AND** 请求和响应 SHALL NOT 包含分类方案 ID

#### Scenario: 创建子分类

- **WHEN** 管理员为档案分类选择父级
- **THEN** 系统 SHALL 校验父级分类存在且未删除
- **AND** 系统 SHALL 拒绝分类循环
- **AND** 系统 SHALL NOT 校验或保存分类方案

## MODIFIED Requirements

### Requirement: 全宗可用分类范围

系统 SHALL 支持全宗显式维护可用分类集合，使业务界面和服务端写入按全宗限定可选择分类。

#### Scenario: 保存全宗可用分类范围

- **WHEN** 管理员为全宗保存可用分类集合和排序
- **THEN** 系统 SHALL 覆盖该全宗已有分类范围
- **AND** 系统 SHALL 校验全宗存在且分类存在并启用
- **AND** 请求和响应 SHALL NOT 包含默认分类标记

#### Scenario: 清空全宗可用分类范围

- **WHEN** 管理员为全宗保存空分类集合
- **THEN** 系统 SHALL 删除该全宗已有分类范围
- **AND** 该全宗 SHALL 没有可用分类

#### Scenario: 查询全宗可用分类范围

- **WHEN** 管理员查询某全宗可用分类范围
- **THEN** 系统 SHALL 返回该全宗显式维护的分类范围列表
- **AND** 响应 SHALL 包含范围关系 ID、全宗编码、分类 ID 和排序
- **AND** 响应 SHALL NOT 包含默认分类标记

### Requirement: 按全宗解析可用分类

系统 SHALL 只返回全宗显式勾选的启用分类，供建档、导入和管理界面选择。

#### Scenario: 全宗已有分类范围

- **WHEN** 客户端按全宗编码查询可用分类
- **THEN** 系统 SHALL 按全宗分类范围的排序返回其中的启用分类
- **AND** 返回分类 SHALL NOT 包含分类方案 ID

#### Scenario: 全宗没有分类范围

- **WHEN** 客户端按全宗编码查询可用分类且该全宗未维护分类范围
- **THEN** 系统 SHALL 返回空集合
- **AND** 系统 SHALL NOT 回退全部分类、默认分类或其他全宗的分类

## REMOVED Requirements

### Requirement: 分类方案管理

**Reason**: 分类方案未提供模板复制、版本或批量采用能力，且与全宗直接选择分类和全局分类树重复。

**Migration**: 删除分类方案资源和 API；现有分类保留自身 ID、父级、编码、名称、字段和动态表信息。

### Requirement: 分类节点归属分类方案

**Reason**: 分类改为全局树节点，不再需要方案外键和同方案父子校验。

**Migration**: 删除分类的方案 ID；父子关系继续按分类 ID 保留并执行存在性和循环校验。
