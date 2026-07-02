# 档案系统 MVP Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把档案系统能力地图中的 MVP 边界落成文档、OpenSpec 合同和后续代码实施切片。

**Architecture:** 本计划先完成规格和路线收口，不在一次变更中实现全部 MVP 代码。MVP 代码后续按全宗/档号/审计、导入导出、档案电子文件、数据范围四个独立 change 推进，每个 change 都以 OpenSpec 验收场景作为真相源。

**Tech Stack:** Markdown、OpenSpec、Spring Boot、Jakarta Data、MyBatis、PostgreSQL、Vite+。

---

### Task 1: 梳理 MVP 缺口文档

**Files:**

- Create: `docs/archive-knowledge/mvp-implementation-gap.md`
- Modify: `docs/archive-knowledge/README.md`

- [x] **Step 1: 写 MVP 差距清单**

创建 `docs/archive-knowledge/mvp-implementation-gap.md`，明确当前已具备、MVP 前必须补齐、推迟到基础能力的内容。

- [x] **Step 2: 更新知识库入口**

在 `docs/archive-knowledge/README.md` 的文档索引中加入 MVP 实施缺口文档。

- [x] **Step 3: 检查占位符**

Run: `rg -n "TBD|TODO|待补" docs/archive-knowledge/mvp-implementation-gap.md`

Expected: 无输出。

### Task 2: 创建 OpenSpec MVP 合同

**Files:**

- Create: `openspec/changes/add-archive-mvp-foundation/proposal.md`
- Create: `openspec/changes/add-archive-mvp-foundation/design.md`
- Create: `openspec/changes/add-archive-mvp-foundation/specs/archive-mvp-foundation/spec.md`
- Create: `openspec/changes/add-archive-mvp-foundation/tasks.md`

- [x] **Step 1: 写 proposal**

说明为什么当前 POC/基础能力文档不足以判断 MVP 已完成，列出新增 `archive-mvp-foundation` 能力。

- [x] **Step 2: 写 design**

固定 MVP 收口方式：先补合同，再分 change 实现代码，不把完整平台能力混入 MVP。

- [x] **Step 3: 写 spec**

新增 `archive-mvp-foundation` 规格，定义 MVP 完成门槛、全宗基础治理、手工档号、导入导出、基础审计、数据范围和档案电子文件的验收场景。

- [x] **Step 4: 写 tasks**

用未勾选任务列出后续实现顺序，避免把规格完成误判为代码完成。

### Task 3: 验证文档和规格

**Files:**

- Check: `docs/archive-knowledge/mvp-implementation-gap.md`
- Check: `openspec/changes/add-archive-mvp-foundation/**`

- [x] **Step 1: 校验 OpenSpec change**

Run: `openspec validate add-archive-mvp-foundation --strict`

Expected: 通过。

- [x] **Step 2: 检查 Markdown 占位符**

Run: `rg -n "TBD|TODO|待补" docs/archive-knowledge openspec/changes/add-archive-mvp-foundation`

Expected: 无输出。

- [x] **Step 3: 检查工作区差异**

Run: `git status --short`

Expected: 只包含本轮新增或修改的文档与 OpenSpec change。
