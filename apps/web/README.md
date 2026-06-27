# React 前端选型

当前 React 子应用参考 Ant Design Pro v6 的技术方向，但不直接照搬 Pro 脚手架运行时。仓库现有前端工具链仍以 Vite+ 为入口，运行和验证继续使用 `vp`。

## 核心库

- `react` / `react-dom`：React 19。
- `antd`：Ant Design 6，基础组件、主题、表单和反馈都优先使用它。
- `@ant-design/pro-components`：按 AntD 6 兼容版本保留，后续只在表格、布局、复杂表单确实收益明确时引入页面代码。
- `keepalive-for-react` / `keepalive-for-react-router`：页签保活和缓存销毁。
- `@tanstack/react-query`：接口请求、缓存、刷新和服务端状态。
- `zustand`：跨路由 UI 状态，例如页签列表；不承载表单字段状态。
- `zod`：接口响应、提交参数和动态表单边界的类型收窄。
- `dayjs`：AntD 日期组件值转换。

## 表单边界

表单状态默认使用 AntD Form，不额外接入 React Hook Form。动态档案字段统一挂在 `dynamicFields` 对象下：

```tsx
<Form.Item name={["dynamicFields", field.fieldCode]} />
```

详情回填时使用：

```tsx
form.setFieldsValue({ dynamicFields: record.dynamicFields });
```

提交前再用 Zod 做边界解析。这样可以保持接近 Vue `Object.assign` 的批量赋值体验，同时避免 AntD Form 与 React Hook Form 两套状态同步。

只有在出现大量非 AntD 控件、需要极细粒度订阅优化，或需要完全脱离 AntD Form 的表单状态时，才重新评估 React Hook Form。

## 真实后端接入

React 子应用沿用 Vue 端的认证合同：

- 当前会话：`GET /api/v1/auth/session`
- 登录：`POST /api/v1/auth:login`
- 登出：`POST /api/v1/auth:logout`
- CAP 安全验证：`/api/v1/auth/cap/`

非登录页会先校验 session；后端返回 401 时跳转到 `/login?redirect=...`。登录页使用 `cap-widget` 获取 `powToken`，登录成功后返回 redirect 指向的业务页面。请求层遇到 401 会广播未登录事件，路由守卫清理会话和页签状态。
