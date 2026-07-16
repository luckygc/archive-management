## Why

项目已经有文件存储基础能力，但缺少独立的文件预览执行边界。预览需要接入 PDF、Office、图片、音视频等多种解析和转换工具，运行时依赖重、失败模式复杂，不应放进 Spring Boot 主进程。

## What Changes

- 新增同仓独立 Go 子项目 `preview/`，作为文件预览服务的首版工程边界。
- 新增预览服务 HTTP 合同，支持健康检查、能力查询、基于 Magika 优先的文件类型识别和同步预览转换。
- 首版只处理预览服务自身能力，不接入档案业务权限、文件存储权限或前端页面。
- 首版转换策略以浏览器可直接预览、PDF 直出、文本直出和外部工具占位为边界；复杂 Office/CAD/音视频转码后续按转换器逐步接入。
- 在根 `Makefile` 增加预览服务构建、测试和运行入口。

## Capabilities

### New Capabilities

- `file-preview-service`: 独立文件预览服务的 HTTP 合同、格式识别、预览策略和转换执行边界。

### Modified Capabilities

- 无。

## Impact

- 新增 `preview/` Go module、服务代码、Dockerfile 和 README。
- 新增 `openspec/changes/add-preview-service/specs/file-preview-service/spec.md`。
- 更新根 `Makefile`，暴露预览服务常用命令。
- 暂不修改 `server/`、`web/`、`file-storage` 现有代码和合同。
