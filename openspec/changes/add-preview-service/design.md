## Context

当前仓库没有 Go 子项目，后端是 `server/` Spring Boot，前端和共享包分别在 `web/`、`mobile/`、`frontend-core/`。文件存储已经有独立规格，但在线预览需要运行 PDF、Office、图片和音视频相关解析/转换工具，这些工具依赖重、资源消耗和失败模式都不同于主业务应用。

预览服务首版采用同仓独立 Go module：代码、测试、镜像和命令在 `preview/` 下维护，运行时作为独立 HTTP 服务部署。主业务系统后续只通过 HTTP 调用预览服务，不把转换进程嵌入 Spring Boot。

## Goals / Non-Goals

**Goals:**

- 新增可独立构建、测试和运行的 `preview/` Go 服务。
- 提供健康检查、能力查询、格式识别和同步预览转换 HTTP API。
- 对常见浏览器可直接预览类型返回 `passthrough` 策略，对 PDF、图片、文本提供首版可用结果。
- 为 LibreOffice、FFmpeg、PDFium、libvips/ImageMagick 等工具保留清晰的转换器边界。
- 将预览服务命令暴露到根 `Makefile`。

**Non-Goals:**

- 不在本 change 接入档案业务权限、电子文件表、审计或下载短链。
- 不实现异步任务队列、分布式缓存、对象存储回写或多节点调度。
- 不在首版完成 Office/CAD/音视频高保真转换；这些格式先返回明确的 `unsupported` 或外部工具缺失错误。
- 不修改现有 `file-storage` 规格和代码。

## Decisions

### 同仓独立 Go module

`preview/` 使用独立 `go.mod`，避免污染 Java 和前端工具链。根 `Makefile` 只提供入口命令，例如 `preview-test`、`preview-build`、`preview-run`，实际 Go 依赖由 `preview/` 自己管理。

替代方案是立即拆独立仓库，但预览 API、前端 viewer 和主系统集成还会快速调整，跨仓会增加同步成本。当前更适合先同仓沉淀合同和实现边界，稳定后再拆仓。

### 首版使用标准库 HTTP 和清晰 internal 包

Go 服务首版不引入 Web 框架。HTTP 处理使用 `net/http`，路由、JSON、multipart、超时和文件流都能用标准库覆盖。包结构按职责拆分：

- `internal/api`: HTTP handler、请求/响应和错误映射。
- `internal/detect`: 文件名、MIME 和魔数识别。
- `internal/preview`: 预览策略和转换服务。
- `internal/converter`: 转换器接口和内置转换器。

替代方案是引入 Gin/Echo/Fiber，但首版 API 少，框架收益不明显。

### 同步转换 API 作为首版闭环

首版提供 `POST /v1/preview:convert`，使用 multipart 上传文件并同步返回 JSON。响应包含策略、输入类型、输出类型、文件名和 base64 内容。这样测试和主系统后续接入都简单。

替代方案是直接做异步任务和缓存产物 URL，但这会马上引入任务状态、清理、鉴权和产物存储，超出首版边界。

### 文件类型识别优先使用 Google Magika

文件类型识别抽象为探测器接口，首选调用 Magika CLI 的 JSON 输出。Magika 是 Google 开源的 AI 文件类型识别工具，支持 CLI、Python、JavaScript/TypeScript 和 Rust；Go binding 仍不作为首版稳定依赖使用。因此 Go 服务通过外部命令调用 Magika，避免引入不稳定 Go SDK。

如果 Magika 命令不存在、超时或返回不可解析结果，服务退回最小 fallback 探测：PDF 魔数、`net/http.DetectContentType`、文件扩展名和 UTF-8 文本判断。fallback 只用于保证服务可用，不作为高质量识别主路径。

替代方案是完全手写魔数和扩展名表，但长期维护成本高，文本格式识别准确率也差。另一个替代方案是等待 Go binding 稳定后直接链接库，但当前没有必要阻塞服务骨架。

### 转换器先支持安全闭环，再逐步扩展重工具

首版内置转换器只处理低风险闭环：

- PDF、图片、音视频等浏览器可直接预览类型返回 `passthrough`。
- UTF-8 文本返回 `text/plain; charset=utf-8`。
- Office、压缩包、CAD、DICOM 等长尾格式识别后返回 `unsupported`，并在能力列表中标注需要后续转换器。

外部工具转换器通过接口预留，但不默认调用本机 LibreOffice/FFmpeg，避免首版在缺少依赖时表现不稳定。

## Risks / Trade-offs

- 同步接口不适合大文件和慢转换 → 首版通过最大上传大小和请求超时兜底；后续重转换再新增异步任务接口。
- Magika 是可选外部命令 → 服务启动不强依赖 Magika；能力接口会返回探测器状态，缺失时使用 fallback。
- `passthrough` 不等于所有浏览器都能显示 → 响应会返回明确策略和 MIME，由前端 viewer 决定是否原生打开。
- 不接入外部工具会让 Office 首版不可预览 → 这是刻意控制范围，先把服务边界、识别和错误合同做稳。
- multipart base64 响应不适合超大产物 → 首版用于服务闭环和集成验证；后续改为产物流式下载或对象存储 URL。

## Migration Plan

1. 新增 `preview/` Go module、HTTP 服务、测试和 README。
2. 更新根 `Makefile`，暴露预览服务测试、构建和运行入口。
3. 使用 `go test ./...` 和 `go build ./cmd/preview-service` 验证。
4. 后续业务集成时由 Spring Boot 通过 HTTP 调用预览服务，不需要回滚现有主应用代码。
