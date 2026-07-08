## ADDED Requirements

### Requirement: MVP 完成门槛

系统 SHALL 只有在必备能力全部具备并通过验收后，才可称为档案系统 MVP。

#### Scenario: 判断 MVP 是否完成

- **WHEN** 团队评估档案系统 MVP 状态
- **THEN** 系统 SHALL 至少具备多全宗基础维护、分类树、动态字段、字段布局、动态表构建、`ArchiveItem` / `ArchiveVolume` 管理、档案关联、明细行表、手工档号唯一校验、档案电子文件管理、管理查询、全文发现、基础权限、导入导出和基础审计
- **AND** 团队 SHALL NOT 因 POC 主链路可演示就判定 MVP 已完成

#### Scenario: 区分 MVP 和基础能力

- **WHEN** 需求涉及分类方案版本、全宗沿革、档号规则引擎、正式归档流程、移交接收、四性检测、利用审批、销毁鉴定或长期保存格式迁移
- **THEN** 团队 SHALL 将该需求归入基础能力或完整能力
- **AND** 团队 SHALL NOT 将其作为当前 MVP 完成的前置条件

### Requirement: 多全宗基础治理

系统 SHALL 在 MVP 中支持多全宗基础维护，并让档案写入引用有效全宗。

#### Scenario: 维护全宗基础信息

- **WHEN** 管理员维护全宗
- **THEN** 系统 SHALL 保存全宗编码、全宗名称、启用状态和排序
- **AND** 全宗编码 SHALL 在未删除全宗中唯一

#### Scenario: 使用停用全宗创建档案

- **WHEN** 客户端使用停用或不存在的全宗编码创建 `ArchiveItem` 或 `ArchiveVolume`
- **THEN** 系统 SHALL 拒绝写入
- **AND** 响应 SHALL 指出全宗不可用

### Requirement: 手工档号与唯一校验

系统 SHALL 在 MVP 中支持手工档号保存，并分别通过主表内建唯一约束和分类动态唯一规则执行唯一性。

#### Scenario: 保存手工档号

- **WHEN** 客户端创建 `ArchiveVolume` 或创建、更新 `ArchiveItem`
- **THEN** 系统 SHALL 允许客户端提交 `archive_no`
- **AND** 系统 SHALL 保存该档号为档案固定字段
- **AND** 后续新增 `ArchiveVolume` 更新能力时，系统 SHALL 对该更新能力复用相同的手工档号校验

#### Scenario: 校验业务档号组成字段唯一性

- **WHEN** 分类配置了包含业务档号组成字段的动态唯一规则
- **THEN** 系统 SHALL 按该唯一规则拒绝重复档案
- **AND** 系统 SHALL NOT 将固定字段 `archive_no` 作为动态字段参与分类唯一规则
- **AND** 系统 SHALL NOT 在 MVP 中要求启用自动档号规则引擎

#### Scenario: 校验手工档号固定字段唯一性

- **WHEN** 客户端在同一分类、同一档案对象层级下创建未删除 `ArchiveVolume` 或创建、更新未删除 `ArchiveItem`，并提交非空 `archive_no`
- **THEN** 系统 SHALL 拒绝与未删除档案重复的 `archive_no`
- **AND** 系统 SHALL 使用 `ArchiveItem` / `ArchiveVolume` 主表内建部分唯一约束分别校验档号唯一性
- **AND** 系统 SHALL 允许已删除档案占用的 `archive_no` 被后续档案重新使用
- **AND** 后续新增 `ArchiveVolume` 更新能力时，系统 SHALL 对该更新能力复用相同的主表唯一校验

### Requirement: 档案电子文件和下载路由

系统 SHALL 在 MVP 中支持档案条目上传、列表可见、下载和删除电子文件，并按电子文件记录下载；在线预览暂不纳入本阶段。

#### Scenario: 使用既有关联表保存档案电子文件

- **WHEN** 系统保存档案条目电子文件
- **THEN** 系统 SHALL 使用 `am_archive_item_electronic_file` 作为档案电子文件真相表
- **AND** 档案电子文件记录 SHALL 保存 `archive_item_id`、`storage_object_id`、`usage_type` 和 `display_order`
- **AND** 档案电子文件记录 SHALL 通过 `(archive_item_id, storage_object_id, usage_type)` 保持唯一
- **AND** 系统 SHALL NOT 在档案电子文件表中复制 `storage_type`、`bucket_name`、`object_key`、文件名、大小或校验值

#### Scenario: 新增档案电子文件

- **WHEN** 客户端为档案条目新增电子文件
- **THEN** 客户端 SHALL 调用 `POST /api/v1/archive-items/{archiveItem}/electronic-files`
- **AND** 请求 SHALL 使用 `multipart/form-data` 提交 `file`
- **AND** 请求 MAY 包含 `usageType` 和 `displayOrder`
- **AND** 系统 SHALL 先写入对象存储和 `am_storage_object` 文件记录，再保存档案条目与存储对象记录的关系
- **AND** 客户端 SHALL NOT 要求用户手工填写 `storageObjectId` 或其他存储对象内部 ID
- **AND** 系统 SHALL NOT 仅保存对象存储裸路径作为业务真相

#### Scenario: 查询档案条目电子文件

- **WHEN** 客户端查询档案条目电子文件
- **THEN** 客户端 SHALL 调用 `GET /api/v1/archive-items/{archiveItem}/electronic-files`
- **AND** 响应 SHALL 使用 `CollectionResponse`，并在 `items` 中返回档案电子文件 ID、档案条目 ID、存储对象记录 ID、用途、排序、原始文件名、文件大小、内容类型、SHA-256 和创建时间
- **AND** 响应 SHALL NOT 暴露 `bucket_name` 或 `object_key`
- **AND** 查询列表 SHALL 只要求档案读取权限，不等同于允许下载文件内容

#### Scenario: 删除档案条目电子文件

- **WHEN** 客户端删除档案条目电子文件
- **THEN** 客户端 SHALL 调用 `DELETE /api/v1/archive-items/{archiveItem}/electronic-files/{electronicFile}`
- **AND** 系统 SHALL 删除档案电子文件记录
- **AND** 系统 SHALL NOT 删除 `am_storage_object` 文件记录或底层对象

#### Scenario: 创建档案电子文件下载短链

- **WHEN** 客户端下载档案条目电子文件
- **THEN** 客户端 SHALL 调用 `POST /api/v1/archive-items/{archiveItem}/electronic-files/{electronicFile}:createDownloadLink`
- **AND** 系统 SHALL 根据文件记录中的 `storage_type`、`bucket_name` 和 `object_key` 路由读取文件
- **AND** 系统 SHALL 使用独立功能权限区分档案电子文件列表可见和下载
- **AND** 系统 SHALL NOT 根据客户端提交的对象存储路径读取文件
- **AND** 系统 SHALL 写入下载审计

### Requirement: 基础导入导出

系统 SHALL 在 MVP 中支持项目内标准 Excel 导入导出。

#### Scenario: 导入档案

- **WHEN** 客户端上传标准 Excel 导入档案
- **THEN** 系统 SHALL 按目标分类字段定义解析固定字段和动态字段
- **AND** 系统 SHALL 校验全宗、分类、字段类型、必填项和唯一规则
- **AND** 系统 SHALL 返回逐行错误信息
- **AND** 系统 SHALL NOT 在存在校验错误时部分写入同一批次

#### Scenario: 导出查询结果

- **WHEN** 客户端导出档案管理查询结果
- **THEN** 系统 SHALL 使用与当前查询一致的分类、全宗、筛选、排序和数据范围条件
- **AND** 系统 SHALL 写入导出审计
- **AND** 系统 SHALL NOT 绕过后端权限导出全量档案

### Requirement: 基础权限和数据范围

系统 SHALL 在 MVP 中于后端执行基础权限和数据范围判断。

#### Scenario: 查询档案列表

- **WHEN** 用户查询档案管理列表、全文发现或档案详情
- **THEN** 系统 SHALL 按用户可访问的全宗和分类范围过滤结果
- **AND** 系统 SHALL NOT 只依赖前端菜单或按钮隐藏限制数据可见性

#### Scenario: 写入越权档案

- **WHEN** 用户创建、更新、删除、锁定、解锁、下载或导出超出其全宗或分类范围的档案
- **THEN** 系统 SHALL 拒绝操作
- **AND** 响应 SHALL 使用项目统一错误模型表达权限不足

### Requirement: 基础操作审计

系统 SHALL 在 MVP 中记录关键档案操作审计。

#### Scenario: 写入操作审计

- **WHEN** 用户创建、修改、删除、锁定或解锁档案条目
- **THEN** 系统 SHALL 记录档案 ID、分类、全宗、操作类型、操作人、操作时间和操作原因

#### Scenario: 查询操作审计

- **WHEN** 超级管理员查询档案条目操作审计
- **THEN** 系统 SHALL 支持按档案 ID、分类、全宗、操作类型和操作时间范围筛选
- **AND** 响应 SHALL 使用项目统一 `CursorPageResponse` 分页合同
- **AND** 审计记录 SHALL 按操作时间倒序、审计 ID 倒序稳定排序
- **AND** 系统 SHALL NOT 对审计查询再套用普通档案数据范围

#### Scenario: 拒绝未认证审计查询

- **WHEN** 未认证用户查询档案条目操作审计
- **THEN** 系统 SHALL 拒绝查询
- **AND** 系统 SHALL NOT 返回任何审计记录

#### Scenario: 拒绝非超级管理员审计查询

- **WHEN** 非超级管理员用户查询档案条目操作审计
- **THEN** 系统 SHALL 拒绝查询
- **AND** 系统 SHALL NOT 返回任何审计记录

#### Scenario: 利用操作审计

- **WHEN** 用户下载档案文件或导出档案查询结果
- **THEN** 系统 SHALL 记录操作人、操作时间、操作对象、查询条件摘要或文件 ID
- **AND** 审计记录 SHALL 支持按档案、分类、全宗和操作类型查询
