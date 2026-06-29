# 档案行业知识库 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 建立项目内档案行业规范知识库，记录权威来源、适用范围、项目影响和采纳建议。

**Architecture:** 知识库只保存摘要、链接、项目理解和候选采纳建议，不复制受版权保护的标准正文。文档按主题拆分，入口文件解释使用边界，目录文件汇总标准清单，专题文件承载对系统设计的影响。

**Tech Stack:** Markdown、项目 `docs/standards` 候选规范治理口径、OpenSpec 作为业务合同真相源。

---

### Task 1: 创建知识库骨架

**Files:**

- Create: `docs/archive-knowledge/README.md`
- Create: `docs/archive-knowledge/standards-catalog.md`

- [x] **Step 1: 写入口文档**

创建 `README.md`，说明知识库不是强制实现合同，业务字段、流程、接口和验收仍需要进入 OpenSpec。

- [x] **Step 2: 写标准目录**

创建 `standards-catalog.md`，按法规、国家标准、行业标准、专题标准分类记录官方链接、适用性和项目动作。

- [x] **Step 3: 自查版权边界**

确认文档没有复制标准正文，只保留摘要、题录、官方链接和项目理解。

### Task 2: 创建专题理解文档

**Files:**

- Create: `docs/archive-knowledge/legal-and-regulatory-baseline.md`
- Create: `docs/archive-knowledge/electronic-archive-system.md`
- Create: `docs/archive-knowledge/filing-transfer-and-single-copy.md`
- Create: `docs/archive-knowledge/metadata-package-and-format.md`
- Create: `docs/archive-knowledge/authenticity-integrity-usability.md`
- Create: `docs/archive-knowledge/domain-accounting-and-erp.md`

- [x] **Step 1: 写法规底座**

记录档案法、实施条例、机关档案管理规定对系统边界的影响。

- [x] **Step 2: 写系统能力要求**

记录电子档案管理系统、电子文件管理系统对功能分区、权限、日志、检索和保存的影响。

- [x] **Step 3: 写归档移交主线**

记录电子文件归档、电子档案移交接收、单套管理对流程状态和验收的影响。

- [x] **Step 4: 写元数据、封装和格式**

记录元数据、XML 封装、长期保存格式对项目动态字段和存储对象的影响。

- [x] **Step 5: 写四性、证据效力和备份**

记录真实性、完整性、可用性、安全性、证据效力维护和备份对系统验收的影响。

- [x] **Step 6: 写会计与 ERP 专题**

记录电子会计档案、财务报销电子凭证、ERP 归档对当前项目专题能力的启发。

### Task 3: 验证文档变更

**Files:**

- Check: `docs/archive-knowledge/*.md`

- [x] **Step 1: 列出新增文件**

Run: `find docs/archive-knowledge -type f | sort`

Expected: 显示入口、目录和 6 个专题文件。

- [x] **Step 2: 检查 Git 差异范围**

Run: `git status --short docs/archive-knowledge docs/superpowers/plans/2026-06-29-archive-industry-knowledge-base.md`

Expected: 只出现新增知识库文档和实施计划。

- [x] **Step 3: 检查知识库 Markdown 明显问题**

Run: `rg -n "TBD|TODO|待补" docs/archive-knowledge`

Expected: 无输出；若有输出，逐项确认不是未完成占位。
