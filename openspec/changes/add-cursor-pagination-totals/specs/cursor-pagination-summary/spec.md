## ADDED Requirements

### Requirement: 游标列表展示分页摘要

工作台中的游标分页列表 SHALL 在新的首页查询获取结果总数，并使用统一分页控件展示总条数和按当前每页条数计算的总页数，同时继续使用不透明 cursor 提供前后翻页。

#### Scenario: 提交新的列表查询

- **WHEN** 用户首次进入列表、提交新的筛选或排序，或修改每页条数
- **THEN** 客户端 SHALL 提交不含 cursor 的 `requestTotal=true` 查询
- **AND** 响应中的 `total` SHALL 在分页控件中显示为总条数和 `ceil(total / limit)` 总页数

#### Scenario: 使用游标翻页

- **WHEN** 用户使用上一页或下一页 token 翻页
- **THEN** 客户端 SHALL 继续提交原有筛选、排序和 `limit`，仅替换 cursor
- **AND** 客户端 SHALL NOT 提交 `requestTotal=true`
- **AND** 分页控件 SHALL 保留本次查询首页取得的总条数和总页数

#### Scenario: 接口暂未返回总数

- **WHEN** 游标分页响应不包含 `total`
- **THEN** 客户端 SHALL 保持前后翻页可用
- **AND** 客户端 SHALL 不显示不准确的总条数或总页数
