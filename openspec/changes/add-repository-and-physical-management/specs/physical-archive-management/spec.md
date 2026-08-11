## ADDED Requirements

### Requirement: 条目和案卷实物对象

系统 SHALL 使用独立实物对象表达条目或案卷是否存在实物载体，并在首期限制每个档案对象至多一个未删除实物对象。

#### Scenario: 为条目创建实物对象

- **WHEN** 有档案更新权限的用户提交一个存在且可访问的档案条目以及实物通用属性
- **THEN** 系统 SHALL 创建引用该条目的实物对象
- **AND** 响应 SHALL 返回实物对象 ID
- **AND** 该条目 SHALL 被视为存在实物档案
- **AND** 新实物对象的保管状态 SHALL 为 `DEPARTMENT_CUSTODY`

#### Scenario: 为案卷创建实物对象

- **WHEN** 有档案更新权限的用户提交一个存在且可访问的案卷以及实物通用属性
- **THEN** 系统 SHALL 创建引用该案卷的实物对象
- **AND** 系统 SHALL NOT 同时引用档案条目

#### Scenario: 所有者不唯一

- **WHEN** 客户端同时提交档案条目 ID 和案卷 ID，或两者均未提交
- **THEN** 系统 SHALL 拒绝创建实物对象

#### Scenario: 重复创建实物对象

- **WHEN** 档案条目或案卷已经存在未删除实物对象
- **THEN** 系统 SHALL 拒绝再次创建
- **AND** 响应 SHALL 说明该档案已经存在实物信息

#### Scenario: 按所有者读取实物对象

- **WHEN** 有档案读取权限和数据范围的客户端查询 `GET /api/v1/archive-items/{archiveItemId}/physical-object` 或 `GET /api/v1/archive-volumes/{archiveVolumeId}/physical-object`
- **THEN** 系统 SHALL 直接返回该档案的单个实物对象
- **AND** 档案不存在实物对象时系统 SHALL 返回状态为 `404` 的 `ProblemDetail`

#### Scenario: 删除实物对象

- **WHEN** 有档案更新权限的用户删除实物对象
- **THEN** 系统 SHALL 逻辑删除实物对象
- **AND** 对应档案 SHALL 被视为不再存在实物档案
- **AND** 已有位置变更历史 SHALL 保留

### Requirement: 实物通用属性与分类扩展

系统 SHALL 将跨分类稳定的实物属性保存在实物对象中，将分类特有属性保留在 `PHYSICAL` 动态字段中。

#### Scenario: 保存实物通用属性

- **WHEN** 客户端创建或更新实物对象
- **THEN** 系统 SHALL 支持保存条码、载体类型、数量、数量单位、完好状况和备注
- **AND** 当前位置 SHALL 仅通过位置关联动作修改

#### Scenario: 保存分类特有实物属性

- **WHEN** 客户端通过现有档案创建或编辑入口提交分类 `PHYSICAL` 动态字段
- **THEN** 系统 SHALL 继续按档案 ID 保存分类特有实物扩展属性
- **AND** 动态字段行存在 SHALL NOT 单独表示该档案存在实物载体

### Requirement: 实物保管状态

系统 SHALL 独立于档案业务库保存实物当前保管状态，状态 SHALL 为 `DEPARTMENT_CUSTODY`、`PENDING_RECEIPT` 或 `ARCHIVE_ROOM_CUSTODY`。

#### Scenario: 已入室藏档案尚未移交实物

- **WHEN** 角色为 `HOLDING` 的档案已存在但其新建实物对象尚未移交
- **THEN** 档案 SHALL 继续属于原室藏库
- **AND** 实物对象 SHALL 为 `DEPARTMENT_CUSTODY`

#### Scenario: 待接收实物不可上架或删除

- **WHEN** 实物对象处于 `PENDING_RECEIPT`
- **THEN** 系统 SHALL 拒绝修改、删除或关联存放位置

#### Scenario: 档案室保管实物上架

- **WHEN** 实物对象处于 `ARCHIVE_ROOM_CUSTODY` 且用户关联有效存放位置
- **THEN** 系统 SHALL 更新当前位置并记录位置历史

### Requirement: 业务部门实物移交接收

系统 SHALL 使用独立实物移交批次表达业务部门向档案室提交实物清单、档案室确认接收或整批退回的过程，并 SHALL NOT 使用档案业务库表达实物移交状态。

#### Scenario: 提交实物移交批次

- **WHEN** 有档案更新权限的用户向 `POST /api/v1/archive-physical-transfers` 提交唯一移交编号、已启用来源部门、1 至 500 个互不重复且处于 `DEPARTMENT_CUSTODY` 的实物对象和可选备注
- **THEN** 系统 SHALL 创建状态为 `PENDING_RECEIPT` 的实物移交批次和清单快照
- **AND** 系统 SHALL 将批次内实物对象原子更新为 `PENDING_RECEIPT`
- **AND** 任一实物对象不存在、不可访问、已经接收或正在其他批次移交时系统 SHALL 拒绝整个批次

#### Scenario: 接收已入室藏档案的实物

- **WHEN** 档案室通过 `POST /api/v1/archive-physical-transfers/{id}:accept` 确认接收包含室藏档案的待接收批次
- **THEN** 系统 SHALL 将批次状态原子更新为 `ACCEPTED`
- **AND** 系统 SHALL 将全部实物对象更新为 `ARCHIVE_ROOM_CUSTODY`
- **AND** 系统 SHALL 保存接收人、接收时间和可选接收说明
- **AND** 对应档案的 ID、档号和当前业务库 SHALL 保持不变
- **AND** 接收动作 SHALL NOT 自动分配真实库位

#### Scenario: 退回实物移交批次

- **WHEN** 档案室通过 `POST /api/v1/archive-physical-transfers/{id}:reject` 提交非空退回原因
- **THEN** 系统 SHALL 将批次状态原子更新为 `REJECTED`
- **AND** 系统 SHALL 将全部实物对象恢复为 `DEPARTMENT_CUSTODY`
- **AND** 系统 SHALL 保存退回人、退回时间和退回原因

#### Scenario: 重复处理实物移交批次

- **WHEN** 客户端接收或退回状态不为 `PENDING_RECEIPT` 的批次
- **THEN** 系统 SHALL 拒绝操作
- **AND** 批次、实物保管状态和档案业务库 SHALL 保持不变

#### Scenario: 查询实物移交批次详情

- **WHEN** 有档案读取权限且可访问批次内全部档案的用户请求 `GET /api/v1/archive-physical-transfers/{id}`
- **THEN** 系统 SHALL 返回批次状态、来源部门、责任人和时间以及不可随实物后续编辑而改变的提交清单快照

### Requirement: 真实库房与层级存放位置

系统 SHALL 使用独立库房表达真实物理空间，并提供归属于库房的层级存放位置。

#### Scenario: 创建真实库房

- **WHEN** 有档案元数据管理权限的用户提交库房编码、名称、启用状态和排序
- **THEN** 系统 SHALL 创建真实库房
- **AND** 真实库房 SHALL NOT 绑定预归档库或正式库等虚拟业务库

#### Scenario: 创建根位置

- **WHEN** 有档案元数据管理权限的用户提交真实库房、位置编码、名称和类型且不提交父位置
- **THEN** 系统 SHALL 创建该库房下的根位置

#### Scenario: 创建子位置

- **WHEN** 用户提交同一真实库房内的父位置
- **THEN** 系统 SHALL 创建子位置
- **AND** 列表 SHALL 通过 `parentId` 和稳定排序表达层级

#### Scenario: 提交无效层级

- **WHEN** 父位置属于其他真实库房、位置引用自身或修改后形成环
- **THEN** 系统 SHALL 拒绝保存
- **AND** 原位置层级 SHALL 保持不变

#### Scenario: 实物接收但尚未上架

- **WHEN** 档案室确认接收实物但尚未关联存放位置
- **THEN** 实物对象 SHALL 为 `ARCHIVE_ROOM_CUSTODY` 且当前位置 MAY 为空
- **AND** 系统 SHALL NOT 因接收动作自动设置存放位置

#### Scenario: 删除仍被使用的位置

- **WHEN** 用户删除仍有未删除子位置或仍被实物对象作为当前位置引用的位置
- **THEN** 系统 SHALL 拒绝删除

### Requirement: 批量关联实物位置

系统 SHALL 在单一事务内批量更新实物当前位置并为每个实际变化追加位置历史。

#### Scenario: 批量关联位置

- **WHEN** 有档案更新权限的用户向 `POST /api/v1/archive-physical-objects:batchAssignLocation` 提交一组实物对象 ID、已启用目标位置和原因
- **THEN** 系统 SHALL 更新全部实物对象的当前位置
- **AND** 系统 SHALL 为每个当前位置发生变化的实物对象记录来源位置、目标位置、业务类型、业务 ID、原因、操作人和操作时间

#### Scenario: 重复关联当前位置

- **WHEN** 某个实物对象已经位于目标位置
- **THEN** 系统 SHALL 保持当前位置不变
- **AND** 系统 SHALL NOT 为该实物对象新增无变化历史

#### Scenario: 批量关联包含无效对象

- **WHEN** 请求中任一实物对象不存在、不可访问，或目标位置不存在或已禁用
- **THEN** 系统 SHALL 拒绝整个批量操作
- **AND** 所有实物对象当前位置 SHALL 保持不变

#### Scenario: 查询位置历史

- **WHEN** 有档案读取权限和数据范围的客户端查询实物对象位置历史
- **THEN** 系统 SHALL 按操作时间和 ID 倒序返回不可覆盖的历史
