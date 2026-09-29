# 项目总规格：Archive Management

## 目标与用户

Archive Management 是面向机构内部使用的档案管理系统。PC 工作台支持档案元数据配置、档案库管理、归档接收、审批待办和系统配置。系统服务档案管理人员、业务部门经办人和系统管理员，不承担营销展示或公共内容发布。

- 档案管理人员维护全宗、分类、字段、布局、档案记录和归档接收流程。
- 业务部门经办人处理待办、提交资料、查询档案状态并办理归档任务。
- 系统管理员维护用户、角色、安全策略和系统参数。

成功的界面应让用户快速理解当前位置、当前对象、可执行动作和操作结果。PC 首屏直接呈现筛选、表格、表单、详情、待办或配置内容；加载、空状态、错误、禁用、提交中和成功反馈均应清楚可见。界面保持克制、可信、清晰、务实，遵循 [Element Plus 设计系统](docs/design-system.md)。

## 能力与验收

- [稳定能力规格](specs/README.md)定义业务字段、状态机、权限、API 和可测试场景。
- [进行中的任务](tasks/README.md)分别维护本次目标、验收要求、实施计划和任务清单；完成后把最终要求并入稳定能力规格。
- [领域词汇](docs/domain-glossary.md)统一术语，[架构总览](docs/architecture.md)记录稳定技术边界。说明文档不能覆盖验收要求。

## 技术与命令

前端使用 Vue 3、TypeScript、Element Plus；后端是单 Spring Boot 应用，数据库使用 PostgreSQL。固定实体使用 Jakarta Data，动态表和复杂 SQL 使用 MyBatis。项目命令以 [mise.toml](mise.toml) 为准：

| 操作 | 命令 |
| --- | --- |
| 服务端编译 | `mise exec -- mvn -f server/pom.xml compile` |
| 服务端格式检查 | `mise exec -- mvn -f server/pom.xml spotless:check` |
| 服务端测试 | `mise exec -- mvn -f server/pom.xml test` |
| 前端检查、测试与构建 | `mise exec -- pnpm --dir frontend run ready` |
| 本地前端预览 | `mise exec -- pnpm --dir frontend run dev:web`，仅由开发者按需启动 |

## 项目结构

| 路径 | 职责 |
| --- | --- |
| `server/` | Spring Boot 主应用与后端测试 |
| `frontend/admin/` | PC 管理工作台 |
| `frontend/packages/core/` | 框架无关的前端共享能力 |
| `specs/` | 当前稳定的业务和 API 验收要求 |
| `tasks/` | 进行中变更的规格、计划和任务 |
| `docs/` | 架构、设计系统、开发、部署、运维和领域资料 |

## 代码风格

后端按业务子域组织，遵守 `web -> service -> manager -> repository/mapper` 依赖方向。Java 使用 JSpecify 可空约定；格式和 import 以 Spotless + AOSP `google-java-format` 为准。以下是现有 Service 的代码形态：

```java
@Service
public class IntakeService {
    public static final String STATUS_LOCAL_PACKAGE_AVAILABLE = "local_package_available";

    public IntakeOverviewDto getOverview() {
        return new IntakeOverviewDto(
                false, STATUS_LOCAL_PACKAGE_AVAILABLE, "本地档案信息包接收可用；暂未配置 NAS、SFTP、HTTP 等外部连接");
    }
}
```

前端遵循现有 Vue 组件、Element Plus 和项目脚本，不新增平行组件库。具体包边界见 [架构总览](docs/architecture.md)，协作规则见 [AGENTS.md](AGENTS.md)。

## 测试策略

业务状态、权限、数据范围、持久化和失败恢复由服务端测试验证；API 边界验证请求与响应；前端测试覆盖用户可见状态和交互。修改范围决定最窄必要验证。涉及包边界时运行 ArchUnit，涉及 PostgreSQL 行为时使用项目已有的集成测试入口。

## 工程边界

- 始终保证服务端校验、权限和数据库约束，前端校验只改善体验。
- 数据库写入、远程调用、文件读写与缓存失效等副作用集中且可追踪。
- 遵守 [AGENTS.md](AGENTS.md) 的仓库规则；修改接口或业务行为时先更新对应能力规格，再实现和验证。
- 不提交密钥，不处理无关工作树改动，不把未实现能力写成已交付能力。

## 完成判据

对应能力规格中的场景可被验证；实现与 API、前端类型和文档一致；必要的构建、检查与测试通过；失败与恢复路径可观察；未完成或未验证的部分在任务清单中明确保留。
