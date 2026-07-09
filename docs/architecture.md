# 架构总览

Archive Management 当前按单 Spring Boot 主应用起步，配套 PC 前端和独立文件预览服务。架构目标是让档案业务能力、基础设施适配和前端工作台边界清楚，避免在未出现真实需求前引入额外平台层。

## 顶层目录

| 路径 | 职责 |
| --- | --- |
| `server/` | Spring Boot 后端主应用，承载 HTTP API、业务模块、迁移、认证、权限和文件存储适配 |
| `web/` | PC 主应用，React + Ant Design 管理界面 |
| `frontend-core/` | 前端共享基础能力，包含 API client、认证状态、CAP 校验和共享类型 |
| `preview/` | 独立 Go 文件预览服务 |
| `openspec/` | API 和业务能力合同 |
| `docs/` | 工程、使用、部署、运维和行业知识文档 |

## 后端分层

后端 Java 包根为 `github.luckygc.am`。

| 包 | 职责 |
| --- | --- |
| `app` | 应用启动类、应用级装配和启动期编排 |
| `module` | 业务模块集合 |
| `infrastructure` | Spring Security、Hibernate、MyBatis、Flyway、缓存、存储、Web 基础设施等技术适配 |
| `common` | 跨业务模块共享的基础约定，不放认证、用户、权限、档案等业务语义 |

业务模块位于 `server/src/main/java/github/luckygc/am/module`：

| 模块 | 当前职责 |
| --- | --- |
| `archive` | 档案元数据、档案条目、案卷、电子文件、导入导出、搜索、治理、本体、规则和数据范围 |
| `authentication` | CAP、登录、会话、用户、登录失败限制和认证审计 |
| `authorization` | 角色和功能权限 |
| `intake` | 归档接收入口 |
| `organization` | 组织部门 |
| `storage` | 文件元数据、短链和下载入口 |

模块内默认依赖方向：

```text
web -> service -> manager -> repository/mapper
```

Controller 不直接依赖 Repository 或 Mapper。跨业务模块复用能力时优先依赖目标模块已有 Service，不绕过目标模块操作底层表。

## 持久化边界

项目当前持久化入口是 Jakarta Data Repository 和 MyBatis：

| 场景 | 入口 |
| --- | --- |
| 固定 CRUD 表 | Jakarta Data Repository |
| 动态表、复杂 SQL、批处理、报表、认证适配查询 | MyBatis |
| 文件内容 | `FileStorageService`，默认本地存储，也支持 S3 兼容对象存储 |

约束：

- 不使用 Spring Data JPA。
- 不把 JdbcClient 作为项目持久化入口。
- 固定实体写入使用语义明确的 insert、update、delete 方法，不使用 Repository save 或 upsert 语义。
- Jakarta Data Repository 通过 Hibernate `StatelessSession` / `EntityAgent` 执行，不依赖一级缓存、脏检查或延迟会话生命周期。
- Repository 不返回 Stream、游标等依赖会话生命周期的对象。

## 数据模型

档案实例数据分为：

- 固定主表：保存全宗、分类、档号、层级、治理版本、状态、锁定、审计等稳定字段。
- 分类动态表：保存不同档案分类的动态字段。
- 明细表：保存档案条目的重复明细行。
- 文件组件表：保存档案电子文件和短链相关记录。
- 治理表：保存治理方案、版本、适用范围、本体、规则和绑定关系。

本体和规则解释语义与行为，不替代档案主数据存储。

## HTTP API

项目自有 API 统一使用 `/api/v1/**`。通用 API 风格、分页、错误响应、ID 合同和异步任务口径以 `openspec/specs/api-contract/spec.md` 为准。

当前实现中：

- 普通资源使用标准 HTTP 方法。
- 标准 CRUD 难以表达的动作使用 Google AIP-136 custom method 形式，例如 `POST /api/v1/archive-rules/{ruleId}:publish`。
- 错误响应使用 Spring `ProblemDetail` / RFC 9457，并追加 `code`、`reason`、`traceId`、`path` 和 `fieldViolations`。
- 会话认证使用 Spring Security + Spring Session JDBC。

## 前端结构

`web/` 是 PC 管理界面，核心约束来自 `PRODUCT.md` 和 `DESIGN.md`。

主要技术：

- React 19。
- Ant Design 6。
- Ant Design Pro Components。
- React Router。
- TanStack Query。
- Zustand。
- Zod。
- Vite+ 工具链。

页面路由由 `web/src/app/routes.tsx` 定义，业务页面放在 `web/src/pages`。前端使用 Ant Design Form 作为默认表单状态源，动态档案字段统一放在 `dynamicFields` 下。

`frontend-core/` 不承载页面和 UI 壳层，只提供共享基础能力。

## 文件预览服务

`preview/` 是独立 Go module，运行时作为单独 HTTP 服务部署。它不嵌入 Spring Boot 主进程，避免把 Office、音视频、CAD 等重转换依赖带入主应用。

当前能力：

- `GET /healthz`
- `GET /v1/capabilities`
- `POST /v1/preview:convert`

首版同步转换只覆盖低风险闭环，复杂格式返回明确不支持或外部工具缺失。

## 运行时基础设施

| 能力 | 使用方式 |
| --- | --- |
| 会话 | Spring Session JDBC，表名 `SPRING_SESSION` |
| 缓存 | Spring Cache，默认 Caffeine |
| 调度 | Spring Quartz JDBC JobStore，表名 `QRTZ_*` |
| 模块事件 | Spring Modulith JDBC Event Publication Registry |
| 流程 | Flowable process engine，禁用 IDM 和 Event Registry |
| 数据库迁移 | Flyway |
| 文件存储 | 本地文件系统或 S3 兼容对象存储 |

基础设施优先使用 Spring Boot AutoConfiguration 和框架标准 Bean，不维护项目级统一 adapter 或自研队列。

## 测试和约束

后端：

- ArchUnit 固化模块边界。
- Maven Enforcer 要求 JDK 25。
- Spotless + google-java-format AOSP 风格统一格式。
- Testcontainers 用于 PostgreSQL 相关测试。

前端：

- `pnpm check` 执行格式、lint 和类型检查。
- `pnpm test` 执行前端测试。
- `pnpm build` 执行构建。

预览服务：

- `go test ./...`。
- `go build ./cmd/preview-service`。
