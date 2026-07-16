## ADDED Requirements

### Requirement: 条目全文投影

系统 SHALL 为 archive item 维护独立全文投影。

#### Scenario: 条目投影包含明细行

- **WHEN** 系统维护条目全文投影
- **THEN** `search_text` SHALL 包含条目固定字段、条目分类动态字段、条目实物字段和条目明细行文本
- **AND** 系统 SHALL NOT 将关联条目的全文内容拼入当前条目投影

### Requirement: 案卷全文投影

系统 SHALL 为 archive volume 维护独立全文投影。

#### Scenario: 案卷投影不拼接卷内条目

- **WHEN** 系统维护案卷全文投影
- **THEN** `search_text` SHALL 只包含案卷自身固定字段、分类动态字段和实物字段
- **AND** 系统 SHALL NOT 将案卷下所有条目全文拼入案卷投影

### Requirement: 条目关联检索边界

条目关联 SHALL 作为结构化关系查询能力，不参与全文投影拼接。

#### Scenario: 关联展示限制深度

- **WHEN** 客户端读取条目详情或关联图
- **THEN** 系统 SHALL 默认只返回一层直接关联
- **AND** 关联图最大深度 SHALL 不超过 2
- **AND** 系统 SHALL 对关联目标执行权限过滤并防止循环展开
