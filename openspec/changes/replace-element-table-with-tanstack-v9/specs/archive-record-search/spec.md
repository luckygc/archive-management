## ADDED Requirements

### Requirement: 档案检索多列排序交互

管理端档案检索结果表 SHALL 允许用户按固定字段或可搜索动态字段设置有优先级的多列排序，并 SHALL 将该顺序映射到既有 `orderBy` 请求。

#### Scenario: 提交多列远程排序

- **WHEN** 用户依次选择多个可排序档案字段
- **THEN** 客户端 SHALL 按界面显示的排序优先级构造 `orderBy`
- **AND** 每个排序项 SHALL 使用字段编码和大写 `ASC` 或 `DESC` 方向
- **AND** 客户端 SHALL 清空旧 cursor 后从第一页重新查询

#### Scenario: 移除一个排序列

- **WHEN** 用户将一个活动排序列切换为未排序
- **THEN** 客户端 SHALL 从 `orderBy` 移除对应字段
- **AND** 其余排序项 SHALL 保持相对优先级

#### Scenario: 清空全部排序

- **WHEN** 用户移除最后一个活动排序列
- **THEN** 客户端 SHALL 不提交 `orderBy`
- **AND** 服务端 SHALL 使用既有稳定默认排序
