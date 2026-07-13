## Context

现有实现用 `StorageType`、`FileStorageBackend` 和 `DelegatingFileStorageService` 同时支持本地目录及多个对象存储名称。对象存储名称最终都由同一个 AWS S3 SDK 实现，并共享同一套 endpoint 和凭证，因此类型区分不能表达真实存储位置；单一配置也无法按历史 `storage_type` 恢复旧 endpoint。

项目尚未发布，没有历史文件兼容负担。统一为 S3 兼容协议后，应用节点保持无状态，单机、集群和云环境只改变基础设施 endpoint。

## Goals / Non-Goals

**Goals:**

- 只维护一套 S3 兼容文件读写实现和配置。
- 删除本地文件后端、adapter 选择和供应商枚举。
- 保留文件记录的 bucket 和 object key，支持内容定位、审计和受控迁移。
- 为原型阶段的开发环境提供无持久化、可重复初始化的 PostgreSQL 和单节点 S3 端点。

**Non-Goals:**

- 不在应用中管理对象存储集群、bucket 生命周期或跨站复制。
- 不提供旧本地文件自动迁移；项目未发布，数据库按目标结构直接维护。
- 不把开发用单节点服务作为生产高可用方案。

## Decisions

### 文件存储服务直接实现唯一后端

`S3CompatibleFileStorageService` 直接实现 `FileStorageService`。服务方法只接收 object key；bucket 来自唯一的 `archive.storage.bucket` 配置，并随上传结果和文件记录保存。读取时校验记录 bucket 与当前配置一致，避免静默从错误位置读取。

删除 `StorageType`、`FileStorageBackend`、`DelegatingFileStorageService` 和 `LocalFileStorageService`。不新增 adapter、工厂或兼容分支。

### 文件记录删除 storage_type

`am_storage_object` 继续保存 `bucket_name` 和 `object_key`，唯一索引调整为二者组合。`storage_type` 在统一 S3 合同下恒定且无法定位 endpoint，删除比固定写入 `S3` 更清楚。

### 配置扁平化

使用 `archive.storage.endpoint`、`region`、`bucket`、`access-key`、`secret-key` 和 `path-style-access`。endpoint 允许留空以使用 AWS SDK 默认 S3 地址；其余必要字段在创建客户端时显式校验。

### 开发端点与生产边界

仓库只保留根 `compose.yaml`，其中仅运行 PostgreSQL 和官方 RustFS 容器，不构建或启动应用、Nginx 及额外初始化容器。PostgreSQL 和 RustFS 数据目录均使用 `tmpfs`，停止容器后直接丢弃原型数据；下次启动通过 Flyway 重建数据库结构。`task infra-up` 在两个服务健康后使用 curl 的 AWS SigV4 支持检查并按需创建开发 bucket，不新增容器，也不让生产应用凭证承担建桶权限。RustFS 采用 Apache 2.0，但当前版本仍为 beta，因此只作为开发依赖。生产配置必须指向 AWS S3、OSS、COS、OBS、Ceph RGW 等外部成熟 S3 兼容服务。

MinIO社区版当前只发布源码，官方旧容器缺少后续安全修复，因此不固化到开发 Compose。

## Risks / Trade-offs

- 单机开发端点不可用时后端文件操作失败，这是唯一存储依赖的预期行为，不提供本地降级。
- 开发 Compose 停止后数据库和对象数据都会丢失，只适用于当前原型阶段，不承载需要保留的数据。
- 切换 endpoint 或 bucket 必须先迁移对象并校验，再更新配置；应用不会同时挂载新旧后端。
- 不同供应商的 S3细节存在差异，验证覆盖项目实际使用的 Put/Get/Head/Delete 和 path-style 行为。
