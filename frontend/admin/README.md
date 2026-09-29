# Vue 3 前端选型

PC 主应用使用 Vue 3 + TypeScript，并继续以 Vite+ 作为统一工具链入口。运行和验证通过 `frontend/package.json` 的 `pnpm` 脚本执行；需要直接调用 Vite+ 时先进入 `frontend/` 再使用 `pnpm exec vp`。

## 核心库

- `vue`：页面与组件运行时。
- `vue-router`：Hash 路由、认证守卫和页面元数据。
- `pinia`：仅保存登录态、权限摘要和页签等全局客户端状态。
- `@tanstack/vue-table`：管理端数据表格的状态与排序。
- `element-plus`：表单、菜单、页签、抽屉、对话框和反馈组件。
- `axios`：由 `frontend/packages/core` 封装统一 API client。
- `zod`：动态字段和必要外部边界的运行时校验。
- `dayjs`：日期输入与 API 字符串转换。
- `vitest` + `@testing-library/vue`：组件和纯逻辑测试。

页面内的查询、表单、加载和提交状态使用 Composition API 就地管理，不引入第二套服务端缓存或表单状态源。

## 页签实例

菜单、面包屑、页签标题和缓存策略统一来自 `frontend/admin/src/app/routes.ts` 的路由树。菜单递归渲染路由树，不限制二级、三级或四级；面包屑直接使用 `RouterView` 提供的匹配路由链。业务路由默认进入缓存；只有明确设置 `meta.cache = false` 的页面不缓存，登录、认证错误等公开路由通过 `meta.menu = false` 或位于工作区路由树之外而不生成菜单。

页签以完整 `fullPath` 作为业务标识，因此同一路由组件携带不同查询参数时可以同时打开多个实例。每个页签通过包装组件获得独立的组件名称和 `instanceId`，打开页签集合据此计算 `KeepAlive include`：关闭页签会移出 include 并销毁缓存，刷新当前页签只递增该页签的 `version` 并重建内部页面组件。

## 真实后端接入

应用沿用后端认证合同：

- 当前用户：`GET /me`
- 登录：`POST /login-sessions`
- 登出当前会话：`DELETE /login-sessions/{session}`

部署要求 TOTP 时，未绑定用户在密码通过后于登录页保存一次性密钥并提交动态验证码；验证成功前不建立会话。非登录页由 Vue Router 守卫校验 session；后端返回 401 时清理会话、权限和页签状态，并跳转到 `/login?redirect=...`。登录成功后返回 redirect 指向的业务页面。

项目自有 API 使用无 `/api` 前缀的根路径，JSON 与 query 字段保持 `camelCase`。档案搜索和导出把多列排序放在 URL 的 `sort` 参数中，复杂业务查询条件放在 JSON 请求体中；cursor 列表直接沿用服务端返回的分页链接。具体协议以[项目 API 合同](../../specs/SPEC-项目API合同.md)及对应业务规格为准。
