## ADDED Requirements

### Requirement: 导入执行运行时规则
档案导入 SHALL 复用正常条目写入 Service 的运行时规则和约束。

#### Scenario: 预检导入行运行时约束
- **WHEN** 系统预检导入文件中的新增或修改行
- **THEN** 系统 SHALL 使用与 `ITEM_BEFORE_CREATE` 或 `ITEM_BEFORE_UPDATE` 相同的字段目录和执行核心进行无写入校验
- **AND** 行错误 SHALL 包含行号、字段代码、定义编码和可展示原因

#### Scenario: 提交导入行
- **WHEN** 导入预检全部通过并开始写入
- **THEN** 每一行 SHALL 调用正常条目创建或修改 Service 并再次执行对应运行时触发点
- **AND** 导入 SHALL NOT 通过直接 Mapper 或批量 SQL 绕过运行时配置、权限、数据范围或数据库约束

### Requirement: 导出执行运行时规则
档案导出 SHALL 在生成导出文件和下载链接前执行 `EXPORT_BEFORE_CREATE`。

#### Scenario: 创建导出结果前执行运行时配置
- **WHEN** 用户提交档案导出请求且权限与数据范围校验通过
- **THEN** 系统 SHALL 使用查询范围、导出数量、分类和当前用户等受控事实执行 `EXPORT_BEFORE_CREATE`
- **AND** 阻断决策 SHALL 阻止生成文件、存储对象、下载链接和成功审计

#### Scenario: 导出产生警告
- **WHEN** 导出规则产生非阻断警告且导出成功
- **THEN** 导出响应 SHALL 返回警告摘要和下载结果
- **AND** 系统 SHALL 在同一成功事务边界记录运行时决策和导出审计
