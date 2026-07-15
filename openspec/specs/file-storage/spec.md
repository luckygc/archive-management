# file-storage Specification

## Purpose

定义文件记录、S3 兼容存储、对象键生成和内容指纹的业务合同，确保文件位置和校验信息都以本地文件记录为真相源。

## Requirements

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

#### Scenario: 识别过期文件

- **WHEN** 文件记录的 `expires_at` 不为空且不晚于当前时间
- **THEN** 系统 SHALL 将该文件记录视为不可用
- **AND** `expires_at` 为空的文件记录 SHALL 表示长期有效
- **AND** 系统 SHALL NOT 依赖操作系统临时目录或对象存储生命周期规则作为业务过期真相源

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

### Requirement: 文件短链访问

系统 SHALL 通过短链向前端暴露文件下载入口，不向前端暴露真实文件存储位置或业务文件流路径。

#### Scenario: 创建用户绑定下载短链

- **WHEN** 前端需要下载需要登录用户权限的文件
- **THEN** 前端 SHALL 先调用对应业务资源的创建下载短链接口
- **AND** 服务端 SHALL 在创建短链前校验当前用户的业务权限、数据范围和业务资源归属关系
- **AND** 服务端 SHALL 将短链绑定到当前用户
- **AND** 服务端 SHALL 返回可由浏览器直接打开的短链下载 URL 和过期时间
- **AND** 前端 SHALL 使用返回的短链 URL 发起下载
- **AND** 前端 SHALL NOT 直接调用真实文件流接口

#### Scenario: 访问用户绑定短链

- **WHEN** 用户通过内部短链下载入口访问文件
- **THEN** 服务端 SHALL 校验短链存在、未过期、未撤销
- **AND** 如果短链绑定了用户，服务端 SHALL 只允许绑定用户访问
- **AND** 如果短链未绑定用户，服务端 MAY 允许已登录用户访问
- **AND** 服务端 SHALL 使用 `application/octet-stream` 返回附件下载响应
- **AND** 服务端 SHALL NOT 使用文件自身 MIME 类型作为下载响应的 `Content-Type`

#### Scenario: 访问公开短链

- **WHEN** 匿名访问公开短链下载入口
- **THEN** 服务端 SHALL 只允许访问未绑定用户的短链
- **AND** 服务端 SHALL 拒绝用户绑定短链
- **AND** 服务端 SHALL 校验短链存在、未过期、未撤销

#### Scenario: 清理过期短链

- **WHEN** 统一过期数据清理服务运行
- **THEN** 系统 SHALL 删除已过期的文件短链
- **AND** 系统 SHALL NOT 因短链过期而删除长期有效的文件对象

### Requirement: 临时对象生命周期

系统 SHALL 以本地文件记录管理临时 S3 对象的过期与清理，并保证对象存储和本地记录最终一致。

#### Scenario: 保存临时对象过期时间

- **WHEN** 系统保存临时 S3 对象
- **THEN** 系统 SHALL 在 `am_storage_object.expires_at` 固化过期时间
- **AND** 系统 SHALL NOT 仅依赖短链过期时间或对象存储生命周期规则判断临时对象是否过期

#### Scenario: 创建临时对象的事务回滚

- **WHEN** 已上传临时 S3 对象的业务事务回滚
- **THEN** 系统 SHALL 删除已上传的 S3 对象
- **AND** 系统 SHALL NOT 保留缺少有效本地记录的临时 S3 对象

#### Scenario: 清理过期临时对象

- **WHEN** 统一过期数据清理服务处理已过期的临时对象记录
- **THEN** 系统 SHALL 先删除 S3 对象
- **AND** S3 对象删除成功后系统 SHALL 硬删除本地记录
- **AND** S3 对象删除失败时系统 SHALL 保留本地记录以供后续重试

#### Scenario: 永久对象不参与清理

- **WHEN** 文件记录的 `expires_at` 为空
- **THEN** 系统 SHALL 将该文件记录视为永久对象
- **AND** 统一过期数据清理服务 SHALL NOT 清理该对象或硬删除其本地记录
