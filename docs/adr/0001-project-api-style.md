# ADR-0001：项目自有 HTTP API 按主题采用外部规范

- 状态：已接受
- 日期：2026-09-28

## 背景

本项目面向机构内部使用，由 PC 工作台调用单个 Spring Boot 主应用。已有 API 使用 `camelCase`、冒号动作、纯 token 分页及项目专用异步和错误字段。用户希望按主题结合成熟规范，避免继续发明项目级传输格式。已有调用方在迁移前仍需使用原合同。

## 决策

- [项目 API 合同](../../specs/SPEC-项目API合同.md)为真相源。新接口的 URL 资源命名、无 URL 版本、排序和分页 page object 采用 [Zalando](https://opensource.zalando.com/restful-api-guidelines/)；冒号动作参考 [Google AIP-136](https://google.aip.dev/136)；JSON/query 的 `camelCase` 采用 [Azure](https://github.com/microsoft/api-guidelines/blob/vNext/azure/Guidelines.md)。
- 部分更新采用 [RFC 5789](https://www.rfc-editor.org/rfc/rfc5789) 与 [RFC 7396](https://www.rfc-editor.org/rfc/rfc7396) 的 JSON Merge Patch；错误采用 [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457)，不为所有错误强制增加项目专用字段。
- 新接口的可增长集合使用 Zalando 的 `cursor`、`limit`、`items` 及分页链接，不规定全局分页大小或自定义按需计数参数；确需总数时可按 Zalando 支持 `Prefer: return=total-count`，服务端可不采纳。是否需要 offset、默认大小和上限由具体接口文档决定。
- 对已有资源执行的新接口长耗时冒号动作采用 [Azure 长耗时操作的状态监视资源](https://github.com/microsoft/api-guidelines/blob/vNext/azure/Guidelines.md#long-running-operations--jobs)：`202 Accepted`、`Operation-Id`、`Operation-Location`、Azure 状态值与轮询约束。监视资源中的 Azure `ErrorDetail` 与普通 HTTP 错误使用的 RFC 9457 Problem Details 分别适用。
- 用户长耗时任务在服务端持久执行。关闭浏览器只停止客户端轮询；重新登录后可从有权限的操作列表恢复监视。任务可靠执行、权限重检、主动取消和结果保留是本项目的业务要求，Azure 只提供状态监视资源与列表的 HTTP 模式。
- 新接口的条件请求、幂等重试、日期时间、缓存和文件响应沿用相应 HTTP RFC 与 Zalando 指引；是否需要条件写入、可重试命令及缓存，由具体资源接口声明。
- 当前路径直接以资源开始，不保留 `/api` 或 `/v1` 别名及重定向。确需并行提供不兼容表示结构时才用 Zalando 媒体类型版本；URL、HTTP 方法或操作语义的变更须新增资源或动作，或先迁移调用方。分页导航已改为包含游标的链接；存量 `requestTotal`、`pageNo/pageSize`、自定义任务与错误字段继续由旧合同约束，迁移完成后移除。

## 替代方案

- 全面采用 Zalando：其 `snake_case` 和避免冒号动作与已选择的接口风格不合；路径命名与版本策略仍采用 Zalando。
- 全面采用 Google AIP-158 分页：其 `page_size/page_token/next_page_token`、翻页时允许修改 page size，以及只提供向后 token，与本项目需要的 `prev` 导航差异较大；[Zalando 分页](https://opensource.zalando.com/restful-api-guidelines/#pagination)更贴近现有形态。
- 复杂只读查询直接使用 [RFC 10008 `QUERY`](https://www.rfc-editor.org/rfc/rfc10008)：该方法已标准化，但当前项目使用的 [Spring Web 7.0.8 `RequestMethod`](https://docs.spring.io/spring-framework/docs/7.0.8/javadoc-api/org/springframework/web/bind/annotation/RequestMethod.html) 尚未列出 `QUERY`；现阶段采用 Google AIP-136 的 `POST ...:search`，待框架和客户端链路支持后再评估。
- 全面采用 Azure：其 `api-version` query、`value/nextLink` 分页与已选择的无版本路径及集合响应不合。

## 后果

- 新接口不复用存量自定义分页、任务或错误字段；具体资源 schema、排序字段和分页大小仍必须在业务规格中明确，因为外部规范不可能替每个资源指定业务值。
- 旧接口迁移须同步后端、前端调用方、规格、文档及测试，尤其要处理分页链接、任务状态和错误字段的破坏性变化。
- 存量 `PATCH` 实现和前端请求目前仍按 `application/json` 处理，不能因合同修订就宣称已支持 RFC 7396；迁移时须验证 `null` 删除、嵌套对象合并、数组整体替换、媒体类型和原有调用方。
