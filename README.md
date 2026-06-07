# Archive Management

档案管理系统，前端使用 Vue 3 + Vite+，后端使用单 Spring Boot 应用。

## 项目约定

- 项目自有数据库表统一使用 `am_模块_表名` 命名。
- 数据库以 PostgreSQL 为唯一优先目标，项目自有 DDL 和查询允许针对 PostgreSQL 优化。
- 第三方框架原生表保留上游默认命名，例如 `SPRING_SESSION`、`QRTZ_*`。

## Development

- Check everything is ready:

```bash
vp ready
```

- Build the frontend:

```bash
vp build
```

- Run the frontend development server:

```bash
vp dev
```
