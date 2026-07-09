# Archive Management

档案管理系统，PC 前端使用 React + Ant Design，后端使用单 Spring Boot 应用。

## 文档入口

- `docs/README.md`：文档总览。
- `docs/development.md`：本地开发、启动和验证。
- `docs/architecture.md`：架构边界、模块划分和运行时基础设施。
- `docs/database.md`：PostgreSQL、Flyway、固定表、动态表和迁移规则。
- `docs/api.md`：HTTP API 使用约定和资源索引。
- `docs/security.md`：认证、会话、权限、数据范围和请求签名。
- `docs/deployment.md`：部署组件、配置覆盖和发布步骤。
- `docs/ops-runbook.md`：健康检查、日志、常见故障和恢复路径。
- `docs/user-guide/README.md`：PC 端页面地图和常见业务流程。
- `openspec/README.md`：OpenSpec 规格和 change 状态索引。

## 功能边界

- 档案治理 foundation 已进入 OpenSpec 合同：治理方案、档案本体核心和本地规则引擎分别对应 `archive-governance-scheme`、`archive-ontology-core` 和 `archive-local-rule-engine`。
- PC 端已提供治理方案、本体管理、本地规则和规则追踪入口，用于维护多行业档案制度差异的控制面配置。
- 档案实例数据仍保存到固定主表、分类动态表、明细表和文件组件相关表；本体和规则只解释语义与行为，不替代档案主数据存储。

## 项目约定

- 项目自有数据库表统一使用 `am_模块_表名` 命名。
- 数据库以 PostgreSQL 为唯一优先目标，项目自有 DDL 和查询允许针对 PostgreSQL 优化。
- 第三方框架原生表保留上游默认命名，例如 `SPRING_SESSION`、`QRTZ_*`。
- 顶层目录中 `web/` 是 PC 前端，`frontend-core/` 是前端共享基础能力，`server/` 是后端。
- 许可证当前未声明；对外开源或分发前必须由项目所有者明确许可证。

## Development

- 首次准备和本地运行详见 `docs/development.md`。

- Java 后端格式化：

```bash
task server-format
task server-format-check
```

后端 Java 使用 Spotless 调用 `google-java-format` 的 AOSP 风格作为最终格式。JetBrains IDE 建议安装并启用 `google-java-format` 插件，项目风格选择 AOSP；IDEA 自带 Code Style 和 `.editorconfig` 只作为编辑辅助。

导入整理最终以 Spotless 为准：`google-java-format` 处理基础格式，Spotless 再按项目 `importOrder` 排序并移除未使用导入。IDEA 插件的 Optimize Imports 可以本地即时使用，但提交前以 `task server-format` 的结果为准。

- 检查全部前端包：

```bash
task frontend-check
```

- 测试全部前端包：

```bash
task frontend-test
```

- 构建全部前端包：

```bash
task frontend-build
```

- 启动 Web 前端开发服务：

```bash
task web-dev
```

该命令会启动长期占用端口的开发服务；自动化检查默认不主动启动。
