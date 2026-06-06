# Vite+ Monorepo Starter

A starter for creating a Vite+ monorepo.

## 项目约定

- 项目自有数据库表统一使用 `am_` 前缀。
- 第三方框架原生表保留上游默认命名，例如 `SPRING_SESSION`、`QRTZ_*`。

## Development

- Check everything is ready:

```bash
vp run ready
```

- Run the tests:

```bash
vp run -r test
```

- Build the monorepo:

```bash
vp run -r build
```

- Run the development server:

```bash
vp run dev
```
