## ADDED Requirements

### Requirement: 条目和案卷实物对象

系统 SHALL 使用独立实物对象表达条目或案卷是否存在实物载体，并在首期限制每个档案对象至多一个未删除实物对象。

#### Scenario: 为条目创建实物对象

- **WHEN** 有档案更新权限的用户提交一个存在且可访问的档案条目以及实物通用属性
- **THEN** 系统 SHALL 创建引用该条目的实物对象
- **AND** 响应 SHALL 返回实物对象 ID
- **AND** 该条目 SHALL 被视为存在实物档案

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

### Requirement: 真实库房与层级存放位置

系统 SHALL 使用独立库房表达真实物理空间，并提供归属于库房的层级存放位置。

#### Scenario: 创建真实库房

- **WHEN** 有档案元数据管理权限的用户提交库房编码、名称、启用状态和排序
- **THEN** 系统 SHALL 创建真实库房
- **AND** 真实库房 SHALL NOT 绑定预归档库、正式库或移交库等虚拟业务库

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

#### Scenario: 虚拟业务库变化

- **WHEN** 档案从正式库进入移交库但实物尚未搬动
- **THEN** 实物当前位置和所属真实库房 SHALL 保持不变
- **AND** 系统 SHALL NOT 根据虚拟业务库自动修改实物位置

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
