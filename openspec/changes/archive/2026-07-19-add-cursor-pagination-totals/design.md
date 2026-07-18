## Context

`CursorPageResponse` 已有可选 `total` 字段，统一 API 合同规定仅在不携带 cursor 的首页 `requestTotal=true` 时执行 count。当前各列表未统一请求或保留总数，`CursorPagination` 也未展示该信息。

## Goals / Non-Goals

**Goals:**

- 所有工作台游标列表的新查询取得并展示总条数和按当前 `limit` 推导的总页数。
- 后续前后翻页继续使用不透明 cursor，复用本次查询的首页总数。
- 自定义游标查询与 Jakarta Data 游标查询遵守相同的首页计数约束。

**Non-Goals:**

- 不引入 offset 分页、页码跳转、末页跳转或后续翻页 count。
- 不改变 `CursorPageResponse` 的 JSON 字段或 API 路径。

## Decisions

- 分页摘要由共享 `CursorPagination` 接收可选 `total` 并计算 `Math.ceil(total / limit)`；这样所有列表保持同一交互与文案。替代方案是在各页面重复计算和展示，容易遗漏且不一致。
- 每个列表查询状态保存首页 `total`；新的筛选、排序或 `limit` 会发起无 cursor 查询并请求总数，`prev`/`next` 请求不传 `requestTotal` 且保留已有总数。替代方案是每页 count，违反 API 合同并放大查询成本。
- 自定义游标查询仅在 `requestTotal=true` 且未提交 cursor 时执行对应 count；Jakarta Data 入口继续依赖 `PageRequest.withTotal()`。替代方案是让客户端估算或在 token 中记录总量，前者不准确，后者无法处理数据变化。

## Risks / Trade-offs

- [复杂筛选首页多一次 count，响应可能变慢] → 只在新查询首页执行，带 cursor 的后续请求不执行；高成本入口仍可在后续变更中独立设计 `:count`。
- [查询过程中数据变化使 total 与后续页数据轻微不一致] → 摘要表示首次查询时的总量，不作为页码定位或数据权限依据。
- [某些列表缺少 count 实现] → 为对应自定义查询补充受原筛选条件约束的 count 测试后再展示总量。

## Migration Plan

前端与后端兼容已有不含 `total` 的响应：摘要在 `total` 缺失时不显示。部署后新查询自动请求总数；回滚只需恢复前端请求与组件展示，API 保持兼容。

## Open Questions

- 无。
