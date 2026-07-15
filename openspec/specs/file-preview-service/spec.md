# file-preview-service Specification

## Purpose

定义 `preview/` Go 文件预览服务的运行边界、文件类型识别、同步预览转换和根任务命令合同。

## Requirements

### Requirement: 预览服务运行边界

系统 SHALL 在 `preview/` 下提供可独立构建和运行的 Go 文件预览服务。

#### Scenario: 检查服务存活状态

- **WHEN** 客户端发送 `GET /healthz`
- **THEN** 服务 SHALL 返回 HTTP 200 和 JSON 状态响应体

#### Scenario: 查询预览能力

- **WHEN** 客户端发送 `GET /v1/capabilities`
- **THEN** 服务 SHALL 返回支持的预览策略和已知文件格式分组

### Requirement: 文件类型识别

预览服务 SHALL 优先使用 Google Magika 识别提交文件的类型，并在 Magika 不可用时回退到本地轻量识别。

#### Scenario: 使用可用的 Magika 结果

- **WHEN** Magika 为上传文件返回状态正常的 JSON 结果
- **THEN** 服务 SHALL 使用 Magika 输出的标签、MIME 类型、分组和文本标记识别文件

#### Scenario: 根据文件字节识别 PDF

- **WHEN** Magika 不可用且 multipart 上传内容以 PDF 文件签名开头
- **THEN** 即使声明的内容类型为通用类型，服务仍 SHALL 将文件识别为 PDF

#### Scenario: 根据 UTF-8 字节识别文本

- **WHEN** Magika 不可用且 multipart 上传内容是有效 UTF-8 文本且不含二进制控制字符
- **THEN** 服务 SHALL 将文件识别为文本

### Requirement: 同步预览转换

预览服务 SHALL 在首期提供同步预览转换接口。

#### Scenario: 透传浏览器可预览文件

- **WHEN** 客户端通过 `POST /v1/preview:convert` 提交浏览器可预览文件
- **THEN** 响应 SHALL 将策略标记为 `passthrough` 并包含 Base64 编码的预览内容

#### Scenario: 明确拒绝不支持的文件

- **WHEN** 客户端通过 `POST /v1/preview:convert` 提交能够识别但不支持预览的文件类型
- **THEN** 服务 SHALL 返回 HTTP 415 和包含已识别格式的 JSON 错误响应

#### Scenario: 拒绝超过大小限制的上传

- **WHEN** 客户端通过 `POST /v1/preview:convert` 提交超过配置上传大小上限的文件
- **THEN** 服务 SHALL 返回 HTTP 413 且不执行转换

### Requirement: 根任务命令集成

仓库 SHALL 通过根目录 `Taskfile.yml` 提供可重复执行的文件预览服务命令。

#### Scenario: 从仓库根目录运行预览测试

- **WHEN** 开发者运行 `task preview-test`
- **THEN** 该任务 SHALL 执行 `preview/` 模块的 Go 测试

#### Scenario: 从仓库根目录构建预览服务

- **WHEN** 开发者运行 `task preview-build`
- **THEN** 该任务 SHALL 从 `preview/cmd/preview-service` 构建预览服务二进制文件
