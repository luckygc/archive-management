## ADDED Requirements

### Requirement: 标准 Excel 导入

系统 SHALL 支持按项目内标准 Excel 模板导入档案记录，并在同一批次内执行原子写入。

#### Scenario: 下载导入模板

- **WHEN** 已认证用户请求目标分类的导入模板
- **THEN** 系统 SHALL 根据分类字段定义生成标准 Excel 模板
- **AND** 模板 SHALL 包含固定字段和该分类允许导入的动态字段
- **AND** 系统 SHALL 校验用户具备创建或编辑功能权限和目标分类数据范围

#### Scenario: 校验导入文件

- **WHEN** 用户上传标准 Excel 导入档案
- **THEN** 系统 SHALL 校验全宗、分类、字段类型、必填项、唯一规则和数据范围
- **AND** 系统 SHALL 按每行是否命中已有档案分别校验创建或编辑功能权限
- **AND** 系统 SHALL 返回逐行错误信息
- **AND** 系统 SHALL NOT 在存在校验错误时写入任何档案记录

#### Scenario: 导入批次原子写入

- **WHEN** 导入文件全部行校验通过
- **THEN** 系统 SHALL 在一个事务内写入本批次档案记录和动态字段
- **AND** 系统 SHALL 为写入档案记录操作审计

### Requirement: 查询结果导出

系统 SHALL 支持按当前查询条件导出档案结果，并应用当前用户功能权限和数据范围。

#### Scenario: 导出查询结果

- **WHEN** 已认证用户请求导出档案管理查询结果
- **THEN** 系统 SHALL 校验用户具备导出功能权限
- **AND** 系统 SHALL 使用当前查询条件、排序和用户数据范围生成导出结果
- **AND** 系统 SHALL NOT 绕过后端范围导出全量档案
- **AND** 前端 SHALL 使用可由浏览器直接打开的下载链接触发导出，不通过 XHR 读取 `Blob`

#### Scenario: 导出写入审计

- **WHEN** 系统完成档案查询结果导出
- **THEN** 系统 SHALL 写入导出操作审计
- **AND** 审计记录 SHALL 包含操作人、操作时间、分类、全宗和查询条件摘要

#### Scenario: 导出无权限数据

- **WHEN** 用户提交的导出条件只命中其数据范围外档案
- **THEN** 系统 SHALL 返回空导出结果或拒绝请求
- **AND** 系统 SHALL NOT 返回范围外档案数据
