## Why

项目当前同时维护本地目录和多种对象存储类型，带来了 adapter 选择、存储类型路由、多 bucket 配置和集群迁移分支。项目尚未正式发布，可以直接收敛为单一 S3 兼容协议，避免长期维护两套文件存储语义。

## What Changes

- 文件内容只通过 S3 兼容协议读写，不再提供本地目录存储后端。
- 删除 `LOCAL`、`MINIO`、`COS`、`OSS`、`OBS` 等存储类型和 adapter 路由，供应商差异只通过 endpoint、region、bucket 和凭证表达。
- 文件记录继续保存 `bucket_name` 和 `object_key`，删除不能定位真实 endpoint 的冗余 `storage_type`。
- 唯一的开发 Compose 只使用临时存储运行 PostgreSQL 和单节点 S3 兼容服务；生产部署使用外部成熟对象存储。
- 更新文件存储规格、配置说明、运维文档和相关测试。

## Capabilities

### New Capabilities

- 无。

### Modified Capabilities

- `file-storage`: 将可选本地/对象存储路由收敛为唯一 S3 兼容存储合同。

## Impact

- 修改 `common/storage`、`infrastructure/storage` 和 `module/storage` 的文件存储合同及实现。
- 直接维护尚未发布的 Flyway 目标结构，删除 `am_storage_object.storage_type`。
- 修改开发 Compose、默认配置、部署和运维文档。
- 不修改文件上传、下载、短链和档案电子文件的 HTTP 合同。
