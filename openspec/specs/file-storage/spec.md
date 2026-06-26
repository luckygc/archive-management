# file-storage Specification

## Purpose

定义文件记录、对象存储路由、对象键生成和内容指纹的业务合同，确保文件位置、历史读取、默认上传位置和校验信息都以本地文件记录为真相源。

## Requirements

### Requirement: 文件元数据本地落库

系统 SHALL 将文件业务真相保存在本地数据库。

#### Scenario: 创建文件记录

- **WHEN** 系统保存上传文件
- **THEN** 系统 SHALL 在本地数据库创建文件记录
- **AND** 每条记录 SHALL 固化自身的 `storage_type`、`bucket_name` 和 `object_key`
- **AND** 系统 SHALL NOT 使用当前全局存储配置推断历史文件位置

#### Scenario: 读取历史文件

- **WHEN** 系统下载、删除或判断文件是否存在
- **THEN** 系统 SHALL 按文件记录中的 `storage_type`、`bucket_name` 和 `object_key` 路由到对应存储
- **AND** 系统 SHALL NOT 依赖对象存储列举或元信息反查作为业务真相源

### Requirement: 文件存储路由

系统 SHALL 通过显式配置选择默认上传位置。

#### Scenario: 选择默认上传位置

- **WHEN** 系统处理新文件上传
- **THEN** 默认上传位置 SHALL 通过 `archive.storage.adapter` 显式选择 `local`、`s3`、`minio`、`cos`、`oss` 或 `obs`
- **AND** 系统 SHALL NOT 因对象存储配置完整就自动优先切换默认上传位置

#### Scenario: 本地存储 bucket

- **WHEN** 默认上传位置为 `local`
- **THEN** 系统 SHALL 使用 `active-local-bucket` 指定的本地 bucket
- **AND** 本地存储 SHALL 允许配置多个 bucket/root

#### Scenario: 对象存储 bucket

- **WHEN** 默认上传位置为对象存储
- **THEN** 系统 SHALL 只配置一个对象存储 bucket
- **AND** 系统 SHALL NOT 为对象存储配置 active bucket

### Requirement: 对象存储协议

对象存储 SHALL 使用 S3 兼容协议表达。

#### Scenario: 接入对象存储

- **WHEN** 系统接入 AWS S3、MinIO、腾讯 COS、阿里云 OSS 或华为云 OBS
- **THEN** 系统 SHALL 使用 S3 兼容协议和同一套对象存储配置
- **AND** 系统 SHALL NOT 为每个云厂商暴露不同的业务合同

### Requirement: 对象键生成

系统 SHALL 使用统一对象键规则保存文件。

#### Scenario: 生成对象键

- **WHEN** 系统保存新文件
- **THEN** `object_key` SHALL 由 `ObjectKeys.generate(originalFilename)` 生成
- **AND** 格式 SHALL 为 `yyyy/MM/dd/{uuid-v7}.{ext}`
- **AND** UUID v7 SHALL 使用开源库 `uuid-creator`
- **AND** 系统 SHALL NOT 手写 UUID v7

#### Scenario: 解析文件名

- **WHEN** 系统解析文件扩展名或路径分隔符
- **THEN** 文件扩展名 SHALL 使用 Apache Commons IO `FilenameUtils.getExtension`
- **AND** 路径分隔符归一化 SHALL 使用 `FilenameUtils.separatorsToUnix`

### Requirement: 文件内容指纹

系统 SHALL 使用 SHA-256 作为文件内容指纹真相源。

#### Scenario: 保存内容指纹

- **WHEN** 系统保存文件记录
- **THEN** 系统 SHALL 保存 `checksum_sha256`
- **AND** `checksum_md5` SHALL 仅作为历史系统或外部系统兼容备用字段
- **AND** 对象存储返回的 `etag` SHALL 只作为存储侧元信息
- **AND** 系统 SHALL NOT 将 `etag` 作为 checksum 真相源
