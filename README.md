# Archive Management

档案管理系统，前端使用 Vue 3 + Vite+，后端使用单 Spring Boot 应用。

## 项目约定

- 项目自有数据库表统一使用 `am_模块_表名` 命名。
- 数据库以 PostgreSQL 为唯一优先目标，项目自有 DDL 和查询允许针对 PostgreSQL 优化。
- 第三方框架原生表保留上游默认命名，例如 `SPRING_SESSION`、`QRTZ_*`。

## Development

- Java 后端格式化：

```bash
make server-format
make server-format-check
```

后端 Java 使用 Spotless 调用 `google-java-format` 的 AOSP 风格作为最终格式。JetBrains IDE 建议安装并启用 `google-java-format` 插件，项目风格选择 AOSP；IDEA 自带 Code Style 和 `.editorconfig` 只作为编辑辅助。

导入整理最终以 Spotless 为准：`google-java-format` 处理基础格式，Spotless 再按项目 `importOrder` 排序并移除未使用导入。IDEA 插件的 Optimize Imports 可以本地即时使用，但提交前以 `make server-format` 的结果为准。

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
