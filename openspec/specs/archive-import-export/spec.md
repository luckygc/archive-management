# archive-import-export Specification

## Purpose

定义档案标准 Excel 导入、按查询结果导出、安全下载短链、批次原子写入、操作审计及数据范围合同，作为导入校验、导出内容和临时文件交付的验收真相源。

## Requirements

### Requirement: 标准 Excel 导入

系统 SHALL 支持按项目内标准 Excel 模板导入档案记录，并在同一批次内执行原子写入。

#### Scenario: 下载导入模板

- **WHEN** 已认证用户请求目标分类的导入模板
- **THEN** 系统 SHALL 根据分类字段定义生成标准 Excel 模板
- **AND** 模板 SHALL 包含固定字段和该分类允许导入的动态字段
- **AND** 系统 SHALL 校验用户具备创建或编辑功能权限和目标分类数据范围

#### Scenario: 创建导入模板下载短链

- **WHEN** 已认证用户请求档案导入模板
- **THEN** 客户端 SHALL 调用 `POST /api/v1/archive-categories/{categoryId}/archive-items:createImportTemplateDownloadLink`
- **AND** 服务端 SHALL 校验创建或编辑权限与分类数据范围
- **AND** 服务端 SHALL 生成临时 S3 对象，且该对象 SHALL 自创建起 10 分钟过期
- **AND** 服务端 SHALL 创建当前用户绑定短链，且该短链 SHALL 自创建起 10 分钟过期
- **AND** 客户端 SHALL 使用临时 `<a>` 打开返回的安全 GET 短链

#### Scenario: 校验导入文件

- **WHEN** 用户上传标准 Excel 导入档案
- **THEN** 系统 SHALL 在写入前逐行校验全宗非空且启用、年度格式和正数、同批次档号重复、动态字段类型和文本长度以及目标数据范围
- **AND** 系统 SHALL 按每行是否命中已有档案分别校验创建或编辑功能权限
- **AND** 系统 SHALL 返回上述预检失败的逐行错误信息
- **AND** 系统 SHALL NOT 在存在任一逐行预检错误时写入任何档案记录

#### Scenario: 导入批次原子写入

- **WHEN** 导入文件全部行预检通过
- **THEN** 系统 SHALL 在一个事务内写入本批次档案记录和动态字段
- **AND** 系统 SHALL 为写入档案记录操作审计
- **AND** 写入阶段发生约束冲突或其他异常时系统 SHALL 回滚本批次事务

### Requirement: 查询结果导出

系统 SHALL 支持按当前查询条件导出档案结果，并应用当前用户功能权限和数据范围。

#### Scenario: 导出查询结果

- **WHEN** 已认证用户请求导出档案管理查询结果
- **THEN** 系统 SHALL 校验用户具备导出功能权限
- **AND** 客户端 SHALL 使用 `POST /api/v1/archive-items:createExportDownloadLink` 在 JSON 请求体中提交当前查询条件
- **AND** 请求体 SHALL 仅使用 `categoryId`、`fondsCode`、`volumeId`、`where`、`relatedGroups` 和 `orderBy` 作为业务查询字段
- **AND** 非空 `keyword` SHALL NOT 属于管理查询导出业务条件，服务端 SHALL 拒绝包含非空 `keyword` 的导出请求
- **AND** 请求体 SHALL NOT 接收或提交 `limit`、`cursor` 和 `requestTotal` 分页控制字段
- **AND** 服务端 SHALL 在同一成功事务中生成 Excel、写入导出审计并保存临时 S3 对象，且该对象 SHALL 自创建起 10 分钟过期
- **AND** 服务端 SHALL 在同一成功事务中创建当前用户绑定短链，且该短链 SHALL 自创建起 10 分钟过期
- **AND** 客户端 SHALL 使用临时 `<a>` 打开返回的安全 GET 短链
- **AND** 客户端 SHALL NOT 通过 XHR 或 fetch 读取导出文件 Blob
- **AND** 系统 SHALL NOT 绕过后端范围导出全量档案

#### Scenario: 导出写入审计

- **WHEN** 系统完成档案查询结果导出
- **THEN** 系统 SHALL 写入导出操作审计
- **AND** 审计记录 SHALL 包含操作人、操作时间、分类、全宗和查询条件摘要

#### Scenario: 导出无权限数据

- **WHEN** 用户提交的导出条件只命中其数据范围外档案
- **THEN** 系统 SHALL 返回空导出结果或拒绝请求
- **AND** 系统 SHALL NOT 返回范围外档案数据

### Requirement: 导入导出执行运行时配置

档案导入和导出 SHALL 复用正常业务 Service 的运行时执行边界。

#### Scenario: 预检和提交导入行

- **WHEN** 系统预检或提交新增、修改导入行
- **THEN** 系统 SHALL 使用 `ITEM_BEFORE_CREATE` 或 `ITEM_BEFORE_UPDATE` 的字段目录和执行核心
- **AND** 提交 SHALL 调用正常条目 Service 并再次执行对应触发点
- **AND** 导入 SHALL NOT 通过 Mapper 或批量 SQL 绕过运行时配置、权限、数据范围或数据库约束

#### Scenario: 生成导出结果

- **WHEN** 用户提交导出请求且权限与数据范围校验通过
- **THEN** 系统 SHALL 在生成文件、对象和短链前执行 `EXPORT_BEFORE_CREATE`
- **AND** 阻断 SHALL 不产生文件、对象、短链或成功审计
- **AND** 非阻断警告 SHALL 随成功导出响应返回并进入决策追踪
