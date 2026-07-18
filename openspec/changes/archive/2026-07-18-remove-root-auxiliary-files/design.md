## Context

上一轮已经把前端、后端和部署配置从根目录下沉，但根目录仍有三份用户明确不需要的辅助文档，`tooling/` 也同时承载前端依赖补丁、跨栈源码检查与 OpenSpec 治理检查。工具消费者已经唯一且稳定，可以继续按所有权归位，而无需保留通用工具桶。

本变更同时影响 pnpm 补丁解析、Docker build context、Taskfile、Perl 测试自身的脚本路径及目录说明。业务代码和运行时接口均不变化。

## Goals / Non-Goals

**Goals:**

- 删除用户明确不采用的三份根文档。
- 删除 `tooling/`，让每份工具资产靠近其实际消费者或治理真相源。
- 保持依赖补丁、源码行数检查、OpenSpec 治理检查和 Docker 构建可用。

**Non-Goals:**

- 不重写 Perl 检查逻辑或调整阈值。
- 不拆分当前跨栈源码行数检查。
- 不修改历史资料中的旧路径叙述。
- 不把安装到用户 Codex 目录的第三方技能纳入 Git。

## Decisions

### 前端资产由前端工作区拥有

将 `cap-widget` 补丁移到 `frontend/patches/`，将 `source-lines.pl` 及其测试移到 `frontend/scripts/`。源码行数检查虽然覆盖前后端，但唯一执行入口位于前端 workspace 的 `check:source-lines`，因此由该工作区持有比继续放在通用目录更可追踪。

替代方案是新建根 `scripts/`，但这只会把 `tooling/` 改名，无法满足按消费者归位的目标。

### OpenSpec 治理脚本由 openspec 拥有

将治理脚本及测试移到 `openspec/scripts/`。它们检查活动 change、规格索引和历史资料声明，职责与 OpenSpec 治理真相源直接对应，根 `Taskfile.yml` 继续提供稳定入口。

替代方案是放入 `docs/`，但脚本检查的核心状态是 OpenSpec 制品而不是文档生成，因此不采用。

### 直接删除不采用的根文档

删除 `CHANGELOG.md`、`CONTRIBUTING.md` 和 `THIRD_PARTY_NOTICES.md`，不创建替代文件。当前文档和构建均不依赖它们；`docs/superpowers/` 中的提及属于历史资料，不跟随当前治理修改。

## Risks / Trade-offs

- [风险] pnpm 补丁相对路径变化导致冻结安装失败 → 重新安装依赖并构建前端 Docker 镜像。
- [风险] Perl 测试内部仍指向旧脚本 → 同步修改自引用并分别运行两组 `prove` 测试。
- [风险] 当前文档继续展示 `tooling/` → 对非历史资料执行精确引用审计。
- [权衡] 源码行数脚本覆盖后端但归属前端 → 保持唯一调用入口和现有逻辑，避免为目录纯度拆出重复实现。

## Migration Plan

1. 移动补丁和两组脚本，更新全部活动引用。
2. 删除三份根文档和空的 `tooling/`。
3. 运行依赖安装、脚本测试、治理检查、前端完整验证及前端镜像构建。
4. 归档纯工程 change 后提交到本地 `main`。

若验证失败，恢复原路径及引用即可；没有数据、运行时状态或外部服务迁移。

## Open Questions

无。
