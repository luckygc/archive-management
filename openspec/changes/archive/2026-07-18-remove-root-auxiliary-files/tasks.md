## 1. 删除根辅助文档

- [x] 1.1 删除根 `CHANGELOG.md`、`CONTRIBUTING.md` 和 `THIRD_PARTY_NOTICES.md`；验证：`test ! -e CHANGELOG.md && test ! -e CONTRIBUTING.md && test ! -e THIRD_PARTY_NOTICES.md` 成功。
- [x] 1.2 审计当前文档和构建入口不再引用上述文件；验证：排除 `docs/superpowers/` 与归档 change 的 `rg` 检索无结果。

## 2. 工具资产归位

- [x] 2.1 将 `cap-widget` 补丁移到 `frontend/patches/` 并更新 pnpm 与 Docker 路径；验证：`task frontend-install` 成功且锁文件依赖版本不漂移。
- [x] 2.2 将源码行数脚本及测试移到 `frontend/scripts/`，同步工作区命令与测试自引用；验证：`prove -v frontend/scripts/source-lines.t` 和 `perl frontend/scripts/source-lines.pl --report` 成功。
- [x] 2.3 将治理脚本及测试移到 `openspec/scripts/`，同步 Taskfile 与测试自引用；验证：`prove -v openspec/scripts/governance-check.t` 成功，且仓库根不再存在 `tooling/`。

## 3. 文档与完整验证

- [x] 3.1 更新当前目录说明并审计全部活动引用；验证：排除当前及归档 change 与历史资料后，`rg 'tooling/'` 无结果，根目录文件清单符合预期。
- [x] 3.2 运行 `task frontend-ready` 和 `docker build -f frontend/admin/Dockerfile --target web .`；预期全部成功，前端构建仅允许既有第三方注解告警；全仓治理检查在纯工程 change 归档后执行。
- [x] 3.3 确认本 change 无业务 delta spec，且所有归档前实施与验证均已完成；归档使用 `--skip-specs`，随后执行全仓治理检查。
