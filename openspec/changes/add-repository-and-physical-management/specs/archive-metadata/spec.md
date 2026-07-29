## ADDED Requirements

### Requirement: PHYSICAL 动态字段边界

`PHYSICAL` 动态字段 SHALL 仅表达分类特有的条目或案卷实物扩展属性，不作为实物存在性、通用实物属性或当前位置的真相源。

#### Scenario: 判断是否存在实物档案

- **WHEN** 系统判断档案条目或案卷是否存在实物档案
- **THEN** 系统 SHALL 以该档案是否存在未删除实物对象为准
- **AND** 系统 SHALL NOT 以 `PHYSICAL` 动态表是否存在数据行为准

#### Scenario: 保存通用实物属性

- **WHEN** 客户端保存条码、载体类型、数量、数量单位、完好状况或当前位置
- **THEN** 系统 SHALL 将字段保存到独立实物对象或位置关联
- **AND** 系统 SHALL NOT 要求每个档案分类重复定义这些字段

#### Scenario: 保存分类特有扩展属性

- **WHEN** 客户端保存仅适用于当前档案分类的实物属性
- **THEN** 系统 SHALL 继续按 `archive_level` 和 `field_scope=PHYSICAL` 路由到对应分类动态表
