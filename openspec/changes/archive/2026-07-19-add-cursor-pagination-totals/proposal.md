## Why

游标分页列表当前只显示前后翻页控件，用户无法判断当前查询结果的总量和按当前每页条数计算的页数。现有 API 已支持首页按需返回总数，需在不改变游标翻页模型的前提下统一交付该信息。

## What Changes

- 游标分页列表在新的首页查询中请求总数，并在后续游标翻页中复用首页取得的总数。
- 通用分页控件展示“共 N 条 · 共 M 页”；保留上一页、下一页和每页条数选择，不提供页码跳转。
- 为尚未响应总数的自定义游标查询补齐 `requestTotal` 的首页计数行为。

非目标：不将列表改为 offset 分页，不在携带 cursor 的后续请求执行 count，不增加跳转到指定页或末页的能力。

## Capabilities

### New Capabilities

- `cursor-pagination-summary`: 在游标分页工作台中稳定展示查询结果总量和推导页数。

### Modified Capabilities

- 无。现有 `api-contract` 已定义 `requestTotal` 和首页计数行为，本变更只补齐客户端使用与展示。

## Impact

- 受影响前端：共享 `CursorPagination`、各列表 API client、列表页面及其测试。
- 受影响后端：所有自定义游标查询的 `requestTotal` 计数路径与测试。
- HTTP 响应结构保持 `CursorPageResponse` 不变，继续使用已有可选 `total` 字段。
