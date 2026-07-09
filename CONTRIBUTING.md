# 贡献指南

本文说明普通协作流程。更细的仓库规则以 `AGENTS.md` 为准。

## 基本原则

- 使用中文交流、写文档和写注释。
- 默认按最小闭环推进，不顺手扩展相邻模块。
- 优先使用现有框架、组件、数据模型和工具链。
- 不为未来假设新增抽象、适配层、配置开关或兼容分支。
- 不提交密钥、密码、客户数据或本地私有配置。

## 开发流程

1. 拉取远程变更。
2. 运行 `task frontend-install` 安装或刷新前端依赖。
3. 阅读相关 OpenSpec、`AGENTS.md` 和已有模块代码。
4. 小步实现改动。
5. 运行对应验证命令。
6. 更新受影响文档。
7. 提交前检查工作树，只包含本次改动。

## 文档变更

文档落点：

- 工程、部署、运维、API、安全和用户说明放在 `docs/`。
- 业务合同、接口字段、状态机、权限边界和验收场景放在 `openspec/specs/` 或对应 change。
- 行业背景、规范理解和项目采纳建议放在 `docs/archive-knowledge/`。
- 产品定位和设计原则分别维护在 `PRODUCT.md` 和 `DESIGN.md`。

如果说明文档和 OpenSpec 冲突，先修正 OpenSpec 或澄清业务合同，再更新说明文档。

## 前端验证

前端改动至少运行相关命令：

```bash
task frontend-check
task frontend-test
```

影响构建、路由、依赖或发布时运行：

```bash
task frontend-build
```

本项目使用 Vite+，日常通过根目录 `package.json` 和 `Taskfile.yml` 的脚本执行。直接调用时使用 `pnpm exec vp ...`。

## 后端验证

后端 Maven 项目根目录是 `server/`。推荐通过任务运行：

```bash
task server-format-check
task server-compile
task server-test
```

只运行 Maven 时必须进入 `server/`：

```bash
cd server
mise exec -- mvn -q -DskipTests test-compile
```

Java 格式以 Spotless + google-java-format AOSP 风格为准。

## 预览服务验证

预览服务位于 `preview/`：

```bash
task preview-test
task preview-build
```

## 数据库和迁移

- PostgreSQL 是唯一优先目标。
- 项目自有表使用 `am_模块_表名`。
- 第三方表保留上游命名。
- SQL 标识符使用小写 snake_case。
- 迁移脚本不假定 schema 固定为 `public`。
- 生产环境不使用 Flyway clean。

## API 变更

设计或修改项目自有 HTTP API 时：

1. 先查 `openspec/specs/api-contract/spec.md`。
2. 按业务模块更新对应 OpenSpec。
3. Controller 请求 DTO 使用具体动作命名，例如 `CreateArchiveCategoryRequest`。
4. 响应 DTO 使用前端视图语义命名，例如 `ArchiveCategoryListItemResponse`。
5. 不直接把持久化实体作为 HTTP 响应合同。

## 许可证

当前仓库未声明开源许可证。对外开源、分发或允许第三方使用前，必须由项目所有者明确许可证和版权声明。
