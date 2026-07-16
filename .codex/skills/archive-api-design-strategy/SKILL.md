---
name: archive-api-design-strategy
description: Use when archive-management work involves project-owned HTTP APIs, Controller URLs or methods, Request/Response DTOs, pagination, search, filtering, sorting, custom methods, LRO/long-running operations, ProblemDetail errors, frontend-facing IDs, or third-party protocol boundaries.
---

# Archive API Design Strategy

## 真相源顺序

1. 读取最近的 `AGENTS.md`。
2. 读取 `openspec/specs/api-contract/spec.md`。
3. 读取对应业务主规格与活动 change delta；业务资源、字段、权限、状态或验收尚未定义时，先补 OpenSpec，再继续设计。
4. 检查当前 Controller、Request/Response、Service 公开边界、前端 types 与 API client、测试；涉及第三方协议时同时检查适配层。

## 决策流程

1. 先确认项目自有资源以及标准 CRUD 能否表达用例；仅在资源语义无法自然表达时，按 `api-contract` 选择 custom method；长耗时任务按合同选择 LRO/job。
2. 由 `api-contract` 决定请求/响应命名、分页、筛选、排序、ID 与错误合同；由业务规格决定业务字段、状态机、权限、数据范围与验收。
3. 仅在真实 HTTP 视图差异或字段出现语义需要时拆分 DTO，并回查合同确定具体命名，保持层间对象最少。
4. 将第三方固定路径或字段隔离在 adapter，由项目 API 继续遵循通用合同。
5. 合同变化时，同步 OpenSpec、Controller/DTO、Service 公开边界、前端 types/client、错误映射与测试。

## 输出模板

先用一句话给出结论，再按真实内容填以下四个 slot。各 slot 只设协调上限，命令使用行内代码；每条直接承载 owner、决策、影响、风险或验证。

1. **Owner（至多 4 条）**：只列本需求需要读取或修改的通用合同、业务规格与 change owner。
2. **Decision（至多 4 条）**：第一条写“其余遵循 `api-contract`”；再写至多 3 条本需求特有的资源/方法/视图决策或推断，并标记事实与推断。通用默认值、字段和 JSON 保留在 owner；仅当本需求明确改变它们时写出改变项。
3. **Impact（至多 5 条）**：列出预计修改项与确需继续查找的“待定位”项，范围限于合同、Controller、DTO、Service 公开边界、前端 client/types、测试或 adapter，并合并同类文件。相关共享基础设施保持不变时，统一收敛为一条“复用现有，无修改证据”。
4. **Gaps & verification（至多 5 条）**：先消去需求与 owner 已定义的事实，再列出仍阻塞的业务语义，以及本需求涉及的兼容性、权限、失败/恢复风险；每条配对对应 owner 或验证命令。没有新增 gap 时，输出一条明确结论并结束本 slot。

最终内容从结论直接进入四个 slot，呈现审查结果而非仓库调查过程。

## 内部检查

完成模板后，在内部核对 owner 一致性、完整 URL 与资源语义、HTTP DTO 隔离、分页/错误/ID 合同、前后端与测试联动、第三方适配边界。
