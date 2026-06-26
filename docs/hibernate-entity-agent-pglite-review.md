# Hibernate EntityAgent 与 PGlite 测试改动审查说明

## 背景

本次修改处理启动时报错：

```text
A component required a bean of type 'jakarta.persistence.EntityAgent' that could not be found.
Bean method 'entityAgent' in 'HibernateConfiguration' not loaded because @ConditionalOnBean (types: javax.sql.DataSource; SearchStrategy: all) did not find any beans of type javax.sql.DataSource
```

排查结论：

- 不是 Hibernate 升级后 JPA 没升级。当前依赖里的 `jakarta.persistence-api:4.0.0-M5` 已包含 `jakarta.persistence.EntityAgent`。
- Hibernate 8 的 `StatelessSession` 实现了 `EntityAgent`，Jakarta Data / Hibernate Processor 生成的 Repository 会注入 `EntityAgent`。
- 原问题来自 `HibernateConfiguration` 类级 `@ConditionalOnBean(DataSource.class)`。该条件放在普通 `@Configuration` 上时，可能在 `DataSource` Bean 注册前被判断，导致整个 Hibernate 配置类被跳过，最终没有 `EntityAgent` Bean。

## 设计决策

### Hibernate 是必需能力

项目当前持久化入口明确包含 Jakarta Data + Hibernate，固定 CRUD 表优先走 Jakarta Data Repository。因此 Hibernate 不应通过项目配置开关启停。

本次明确采用：

- `HibernateConfiguration` 始终作为普通 Spring 配置加载。
- 需要 `DataSource` 时由 Spring 正常注入；如果没有 `DataSource`，启动应失败，而不是静默跳过 Hibernate。
- 不保留 `am.hibernate.enabled` 这类开关。

### EntityAgent Bean 形态

`EntityAgent` Bean 仍然由项目手动暴露：

- Bean 类型：`jakarta.persistence.EntityAgent`
- Scope：`prototype`
- 实际返回：事务绑定的 `StatelessSession`

这样与 Hibernate Processor 生成的 Jakarta Data Repository 注入需求一致，也避免业务模块直接依赖 Hibernate `Session` / `StatelessSession`。

### 测试数据库选型

用户提出使用 wasm pg 测试。Context7 查询 PGlite 文档后确认：

- PGlite 是 WASM PostgreSQL，主要运行在 browser / Node.js / Bun / Deno。
- `@electric-sql/pglite-socket` 提供 `pglite-server`，可以通过 TCP 暴露 PostgreSQL 协议。
- 标准 PostgreSQL 客户端可连接该 socket server。

因此本次采用：

- 测试启动 `node_modules/.bin/pglite-server --db=memory://`
- Spring Boot 通过 PostgreSQL JDBC 连接该 socket server
- Hibernate / Jakarta Data 看到的是正常 JDBC `DataSource`

没有采用“纯 JVM WASM 直连”方案。原因是 Spring/Hibernate 需要的是 JDBC `DataSource`，纯 JVM WASM 会变成自研 PostgreSQL 协议或 JDBC 适配，范围过大。

## 修改文件

### `server/src/main/java/github/luckygc/am/infrastructure/hibernate/HibernateConfiguration.java`

主要变化：

- 删除类级 `@ConditionalOnBean(DataSource.class)`。
- 删除 `StatelessSession` Bean 暴露方式。
- 新增 prototype `EntityAgent` Bean：

```java
@Bean
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
EntityAgent entityAgent(TransactionalStatelessSessionContext context) {
    return context.currentSession();
}
```

影响：

- Hibernate 配置不再根据 `DataSource` 条件跳过。
- Jakarta Data 生成 Repository 可以注入 `EntityAgent`。
- 如果运行环境没有 `DataSource`，应用启动会明确失败。

### `server/src/test/java/github/luckygc/am/test/PgliteTestDatabase.java`

新增测试工具类，用于 JUnit 测试内启动和关闭 PGlite socket server。

职责：

- 分配本机空闲端口。
- 启动 `node_modules/.bin/pglite-server`。
- 使用 `memory://` 内存数据库。
- 等待 TCP 端口可连接。
- 向 Spring `DynamicPropertyRegistry` 注册：
    - `spring.datasource.url`
    - `spring.datasource.username`
    - `spring.datasource.password`
    - `spring.datasource.driver-class-name`
- 测试结束后销毁进程。

边界：

- 只用于测试，不参与生产代码。
- 依赖 Node.js 和已安装的 pnpm 依赖。
- 不依赖本机 PostgreSQL 服务。

### `server/src/test/java/github/luckygc/am/ServerApplicationTests.java`

主要变化：

- 不再排除 `DataSourceAutoConfiguration`。
- 通过 `@DynamicPropertySource` 注入 PGlite JDBC 连接参数。
- 保留排除 Flyway 和 Spring Session JDBC，避免上下文测试承担数据库迁移和 Session 表初始化。
- 设置 `spring.quartz.auto-startup=false`，避免未跑 Flyway 时 Quartz 访问 `qrtz_locks` 表失败。

测试目标变为：

- Spring Boot 上下文可启动。
- DataSource 自动配置真实生效。
- Hibernate `SessionFactory` 能创建。
- `EntityAgent` Bean 能注册。
- Jakarta Data 生成 Repository 的核心注入链路不再缺 Bean。

### `package.json`

新增测试相关 devDependencies：

```json
"@electric-sql/pglite": "catalog:",
"@electric-sql/pglite-socket": "catalog:"
```

说明：

- `@electric-sql/pglite` 提供 WASM PostgreSQL。
- `@electric-sql/pglite-socket` 提供 `pglite-server` CLI 和 PostgreSQL socket server。

### `pnpm-workspace.yaml`

新增 catalog 版本：

```yaml
"@electric-sql/pglite": 0.5.3
"@electric-sql/pglite-socket": 0.2.4
```

### `pnpm-lock.yaml`

由 `pnpm add -D @electric-sql/pglite@0.5.3 @electric-sql/pglite-socket@0.2.4` 更新。

## 验证结果

已执行：

```bash
mvn -q test
mvn -q spotless:apply
mvn -q test
vp check --fix
vp check
```

结果：

- Maven 后端测试通过。
- Spotless 格式化通过。
- Vite+ 前端检查通过。
- 测试日志确认 Hibernate 使用 PGlite JDBC URL 启动，例如：

```text
Database JDBC URL [jdbc:postgresql://127.0.0.1:<port>/postgres?sslmode=disable]
Database driver: PostgreSQL JDBC Driver
Database dialect: PostgreSQLDialect
Database version: 18.3
```

已额外确认：

```bash
pgrep -af '[p]glite-server' || true
```

没有残留 `pglite-server` 进程。

## 审查关注点

建议重点审查以下点：

- `HibernateConfiguration` 不再条件化是否符合项目“Hibernate 必须存在”的架构要求。
- `EntityAgent` 作为 prototype Bean 暴露是否满足 Hibernate Processor 生成 Repository 的注入模型。
- `PgliteTestDatabase` 通过外部进程启动 `pglite-server` 是否符合测试可维护性要求。
- `ServerApplicationTests` 关闭 Quartz 自动启动是否合理；该测试目标是上下文和 Hibernate 注入链路，不验证 Quartz 表结构。
- Node/pnpm 测试依赖进入后端 Maven 测试链路是否可以接受。当前收益是不依赖本机 PostgreSQL 服务即可跑真实 PostgreSQL JDBC。

## 已知限制

- PGlite socket server 不是完整生产 PostgreSQL 进程，适合上下文启动、基础 SQL、轻量集成测试，不应替代需要验证 PostgreSQL 扩展、并发、执行计划、锁行为的测试。
- `PgliteTestDatabase` 依赖 `node_modules/.bin/pglite-server` 已存在。干净环境需要先执行项目依赖安装。
- 如果未来后端测试需要 Flyway 全量迁移，需单独评估 PGlite 对项目 DDL、扩展和 PostgreSQL 特性的覆盖程度。
- Flowable 兼容性测试仍使用既有本地数据库配置，不在本次 PGlite 改动范围内。
