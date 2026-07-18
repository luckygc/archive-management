## 1. 前端工作区下沉

- [x] 1.1 将根 `package.json`、`pnpm-lock.yaml`、`pnpm-workspace.yaml` 和 `vite.config.ts` 移到 `frontend/`，删除无消费者的根 `tsconfig.json` 并修正工作区相对路径；验证：`task frontend-install` 成功且 `git diff -- frontend/pnpm-lock.yaml` 不出现依赖版本漂移。
- [x] 1.2 更新根任务、前端 Dockerfile、Vite+ 别名与编辑器配置，使前端命令从 `frontend/` 工作区执行；验证：`task frontend-ready` 通过，`git status --short` 在重复构建后不新增生成文件差异。
- [x] 1.3 将 Vite+ 提交钩子入口下沉到前端并显式从 `frontend/` 加载配置；验证：`task frontend-install` 后 `git config --get core.hooksPath` 指向 `frontend/.vite-hooks/_`，且实际 pre-commit 钩子成功运行。

## 2. 后端与部署配置下沉

- [x] 2.1 将 `rewrite.yml` 移到 `backend/archive-server/` 并修正 Maven 配置；验证：在后端目录运行 `mise exec -- mvn --batch-mode --no-transfer-progress -DskipTests rewrite:dryRun` 返回 `BUILD SUCCESS`，且不报告配置文件缺失。
- [x] 2.2 将 `compose.yaml` 移到 `deploy/compose.dev.yaml`，更新根任务和当前文档；验证：`docker compose -f deploy/compose.dev.yaml config --quiet` 成功，且 `task --dry --verbose infra-up` 输出显式配置路径而不启动服务。

## 3. 根目录与文档收口

- [x] 3.1 更新 README、开发、部署和贡献文档中的工作区与配置路径；验证：精确 `rg` 审计不再发现指向已移除根配置的活动引用。
- [x] 3.2 审计根目录只保留跨仓库入口、仓库元数据和顶层说明；验证：根目录不存在 `package.json`、`pnpm-lock.yaml`、`pnpm-workspace.yaml`、`vite.config.ts`、`tsconfig.json`、`rewrite.yml` 或 `compose.yaml`，而对应新路径均存在。

## 4. 完整验证

- [x] 4.1 运行 `task frontend-ready`、`task server-format-check`、`task server-test`、`task server-package`、`task preview-test`、`task preview-build` 和三份 Docker 构建；预期所有命令成功，前端构建仅允许既有第三方注解告警。
- [x] 4.2 确认本 change 不含业务 delta spec，且所有规划制品与实现任务均已完成；归档使用基础设施变更的 `--skip-specs` 路径，归档后再运行仓库治理检查。
