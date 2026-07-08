# 文件预览服务

`preview/` 是档案管理系统的独立文件预览服务。它和 Spring Boot 主应用同仓维护，但运行时作为独立 HTTP 服务部署。

## 当前能力

- `GET /healthz`: 健康检查。
- `GET /v1/capabilities`: 查询首版格式和策略能力。
- `POST /v1/preview:convert`: multipart 同步预览转换。

文件类型识别优先调用 Google Magika CLI。没有安装 Magika 时，服务会退回最小本地探测逻辑，仍可启动和处理 PDF、常见媒体、文本等基础类型。

Magika 可按官方方式安装，例如：

```bash
pipx install magika
```

或：

```bash
curl -LsSf https://securityresearch.google/magika/install.sh | sh
```

首版只内置低风险闭环：

- PDF、常见图片、音频和视频返回 `passthrough` 预览结果。
- UTF-8 文本返回 `text` 预览结果。
- Office、压缩包、CAD、DICOM 等格式会识别后返回不支持，后续通过转换器接入 LibreOffice、FFmpeg、PDFium、libvips/ImageMagick 等工具。

## 运行

```bash
make preview-run
```

默认监听 `:8088`。可用环境变量：

- `PREVIEW_ADDR`: 监听地址，默认 `:8088`。
- `PREVIEW_MAX_UPLOAD_BYTES`: 最大上传大小，默认 `52428800`。
- `PREVIEW_MAGIKA_COMMAND`: Magika 命令路径，默认 `magika`。
- `PREVIEW_MAGIKA_TIMEOUT`: Magika 探测超时，默认 `5s`。
- `PREVIEW_READ_HEADER_TIMEOUT`: 请求头读取超时，默认 `5s`。
- `PREVIEW_READ_TIMEOUT`: 请求读取超时，默认 `30s`。
- `PREVIEW_WRITE_TIMEOUT`: 响应写入超时，默认 `30s`。
- `PREVIEW_IDLE_TIMEOUT`: 空闲连接超时，默认 `60s`。

## 请求示例

```bash
curl -F 'file=@sample.pdf' http://localhost:8088/v1/preview:convert
```

响应中的 `contentBase64` 是首版同步接口的预览载荷。大文件、慢转换和可复用缓存后续应改为异步任务和产物 URL。
