## MODIFIED Requirements

### Requirement: 文件元数据本地落库

系统 SHALL 将文件业务真相保存在本地数据库。

#### Scenario: 创建文件记录

- **WHEN** 系统保存上传文件
- **THEN** 系统 SHALL 在本地数据库创建文件记录
- **AND** 每条记录 SHALL 固化自身的 `bucket_name` 和 `object_key`
- **AND** 系统 SHALL NOT 使用对象存储列举或元信息反查作为业务真相源

#### Scenario: 读取文件

- **WHEN** 系统下载、删除或判断文件是否存在
- **THEN** 系统 SHALL 使用文件记录中的 `bucket_name` 和 `object_key` 定位文件
- **AND** 文件记录中的 bucket 与当前配置不一致时系统 SHALL 明确拒绝访问

### Requirement: 统一 S3 兼容存储

系统 SHALL 只通过 S3 兼容协议保存和读取文件内容。

#### Scenario: 配置文件存储

- **WHEN** 系统创建文件存储服务
- **THEN** 系统 SHALL 使用 `archive.storage` 下的 endpoint、region、bucket、access-key、secret-key 和 path-style-access 配置 S3 客户端
- **AND** 系统 SHALL NOT 提供本地目录 adapter 或供应商类型 adapter

#### Scenario: 接入不同对象存储

- **WHEN** 系统接入 AWS S3、RustFS、MinIO、腾讯 COS、阿里云 OSS、华为云 OBS 或 Ceph RGW
- **THEN** 供应商差异 SHALL 只通过 S3 兼容配置表达
- **AND** 系统 SHALL NOT 将供应商名称保存为文件业务类型

#### Scenario: 开发环境启动对象存储

- **WHEN** 开发者启动仓库 Compose 服务
- **THEN** Compose SHALL 启动单节点 S3 兼容开发服务
- **AND** Compose SHALL 只包含 PostgreSQL 和单节点 S3 兼容服务
- **AND** 原型阶段的开发数据库和对象内容 SHALL 使用无持久化临时存储
- **AND** 开发基础设施启动任务 SHALL 在服务健康后幂等创建后端默认使用的开发 bucket
- **AND** 文档 SHALL 明确该单节点服务不属于生产高可用方案

## REMOVED Requirements

### Requirement: 文件存储路由

**Reason**: 项目不再同时维护本地和多种对象存储后端，默认 adapter、active local bucket 和历史类型路由不再存在。

**Migration**: 项目尚未发布，不保留旧本地文件兼容路径；开发数据按需重建。
