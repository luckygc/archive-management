## ADDED Requirements

### Requirement: 导入校验全宗显式分类范围

档案导入 SHALL 校验每行目标全宗已显式选择本次导入分类，并在预检和提交阶段保持相同边界。

#### Scenario: 预检全宗未选择导入分类

- **WHEN** 导入行目标全宗未显式选择本次导入分类
- **THEN** 系统 SHALL 返回该行 `categoryId` 分类范围错误
- **AND** 系统 SHALL NOT 写入本批次任何档案

#### Scenario: 提交前分类范围发生变化

- **WHEN** 导入预检通过后、提交写入前目标全宗取消勾选本次导入分类
- **THEN** 正常档案条目 Service SHALL 再次拒绝写入
- **AND** 系统 SHALL 回滚本批次已发生的全部写入
