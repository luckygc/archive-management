## 1. 分页摘要与查询状态

- [x] 1.1 扩展共享 `CursorPagination`，在存在 `total` 时展示总条数和按当前 `limit` 推导的总页数；验证：`task web-test` 通过且组件测试覆盖显示与缺失总数两种状态。
- [x] 1.2 为采用响应对象状态的档案查询、档案库、案卷、关联、明细行和规则追踪列表在新查询时请求并向分页控件传递 `total`；验证：`task web-test` 通过且翻页请求不携带 `requestTotal`。
- [x] 1.3 为认证、会话、待办、审批定义与审批中心等采用独立 cursor 状态的列表保留首页 `total` 并传递给分页控件；验证：`task web-test` 通过且修改分页大小或切换筛选会刷新摘要。

## 2. 后端总数支持

- [x] 2.1 为自定义游标查询补齐仅首页 `requestTotal=true` 的受筛选条件约束 count，并保持后续 cursor 请求不计数；验证：`task server-test` 通过相关分页服务测试。

## 3. 验证与治理

- [x] 3.1 运行前端检查和测试、后端相关测试及 OpenSpec 治理检查；验证：`task web-check`、`task web-test`、`task server-test` 和 `task governance-check` 全部通过。
