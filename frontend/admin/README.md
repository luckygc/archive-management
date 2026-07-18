# Vue 3 前端选型

PC 主应用使用 Vue 3 + TypeScript，并继续以 Vite+ 作为统一工具链入口。运行和验证通过根目录 `package.json` 的 `pnpm` 脚本执行；需要直接调用 Vite+ 时使用 `pnpm exec vp`。

## 核心库

- `vue`：页面与组件运行时。
- `vue-router`：Hash 路由、认证守卫和页面元数据。
- `pinia`：仅保存登录态、权限摘要和页签等全局客户端状态。
- `element-plus`：表格、表单、菜单、页签、抽屉、对话框和反馈组件。
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

- 当前用户：`GET /api/v1/me`
- 登录：`POST /api/v1/login-sessions`
- 登出当前会话：`DELETE /api/v1/login-sessions/{session}`
- CAP 创建挑战：`POST /api/v1/cap-challenges`
- CAP 兑换令牌：`POST /api/v1/cap-tokens`
- CAP 校验令牌：`POST /api/v1/cap-tokens:validate`

非登录页由 Vue Router 守卫校验 session；后端返回 401 时清理会话、权限和页签状态，并跳转到 `/login?redirect=...`。登录页通过框架无关的 CAP controller 获取 `powToken`，成功后返回 redirect 指向的业务页面。
