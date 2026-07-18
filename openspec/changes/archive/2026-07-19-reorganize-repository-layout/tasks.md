## 1. 目录迁移

- [x] 1.1 将 `server/` 和 `preview/` 分别迁移到 `backend/archive-server/`、`backend/preview-service/`；运行 `test -f backend/archive-server/pom.xml && test -f backend/preview-service/go.mod && test ! -e server && test ! -e preview`，预期命令成功且旧目录不存在。
- [x] 1.2 将 `web/`、`frontend-core/` 和根级 `test/` 分别迁移到 `frontend/admin/`、`frontend/packages/core/`、`frontend/test/`；运行 `test -f frontend/admin/package.json && test -f frontend/packages/core/package.json && test -f frontend/test/setup.ts && test ! -e web && test ! -e frontend-core && test ! -e test`，预期命令成功。
- [x] 1.3 将 `scripts/` 和 `patches/` 分别迁移到 `tooling/scripts/`、`tooling/patches/`；运行 `test -f tooling/scripts/governance-check.pl && test -f tooling/patches/cap-widget@0.1.56.patch && test ! -e scripts && test ! -e patches`，预期命令成功。

## 2. 构建与部署入口

- [x] 2.1 更新 `Taskfile.yml`、根 `package.json`、`pnpm-workspace.yaml`、Vite 配置、前端测试设置和源码行治理脚本中的路径；运行 `task frontend-install && pnpm exec vp config`，预期 workspace 安装成功且 Vite+ 配置可加载。
- [x] 2.2 将主应用和管理前端容器定义归属到各自应用，更新部署配置路径和忽略规则；运行 `docker build -f frontend/admin/Dockerfile --target web . && docker build -f backend/archive-server/Dockerfile --target server .`，预期两个镜像目标均构建成功。
- [x] 2.3 校准所有新生成目录的 Git 忽略规则；运行 `git check-ignore backend/archive-server/logs backend/archive-server/target frontend/admin/dist backend/preview-service/build frontend/admin/node_modules frontend/packages/core/node_modules`，预期逐项返回匹配规则且没有生成物进入版本控制。

## 3. 真相源与说明文档

- [x] 3.1 更新 `AGENTS.md`、根 README、贡献指南、稳定架构、开发、部署及预览服务当前文档中的有效路径；运行 `rg -n -P '(?<![A-Za-z0-9_-])(?:server|preview|web)/|(?<!@archive-management/)frontend-core/|(?<!tooling/)(?:scripts|patches)/' AGENTS.md README.md CONTRIBUTING.md docs --glob '!docs/superpowers/**'`，预期不再出现作为仓库路径使用的旧目录引用。
- [x] 3.2 更新文件预览服务当前规格的用途和路径合同，并保留历史归档原文；运行 `openspec validate reorganize-repository-layout --strict --no-interactive`，预期变更严格校验通过。

## 4. 验证与收尾

- [x] 4.1 运行 `task governance-check`，预期 OpenSpec 与项目治理检查全部通过。
- [x] 4.2 运行 `task frontend-ready`，预期前端检查、测试和构建全部通过。
- [x] 4.3 运行 `task server-format-check && task server-test && task server-package`，预期 Java 格式、测试和打包全部通过。
- [x] 4.4 运行 `task preview-test && task preview-build`，预期 Go 测试和二进制构建全部通过。
- [x] 4.5 运行 `rg -n -P '(?<![A-Za-z0-9_-])(?:server|preview|web)/|(?<!@archive-management/)frontend-core/|(?<!tooling/)(?:scripts|patches)/' --glob '!openspec/changes/archive/**' --glob '!openspec/changes/reorganize-repository-layout/**' --glob '!docs/superpowers/**' --glob '!node_modules/**' --glob '!**/target/**' --glob '!**/dist/**' --glob '!**/build/**'` 并检查 `git status --short`，预期当前生效文件无未解释的旧路径，工作树仅包含本变更需要的迁移与修改。
