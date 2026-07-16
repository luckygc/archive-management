# 档案分类编码全局唯一设计

## 背景

归档 `add-fonds-classification-adoption` 后，长期规格与运行时模型出现冲突：分类方案规格允许不同方案复用分类编码，数据库也只约束 `(scheme_id, category_code)`；但动态表名仅由分类编码、item/volume 对象类型和字段域生成，档案读取与全文投影也按分类编码解析分类。若两个方案使用相同编码，系统可能路由到同一物理表或解析到错误分类。

项目尚未正式发布，不需要保留方案内重复编码的兼容分支。为保持动态表稳定业务键、历史档案解析和现有查询结构不变，分类编码收敛为跨分类方案永久全局唯一，并在创建后保持不可修改。

## 目标与非目标

目标：

- 全部历史档案分类使用永久全局唯一 `category_code`，逻辑删除不释放编码。
- 分类编码创建后不可修改；创建重复编码时返回可理解的统一错误。
- 数据库约束负责并发写入下的最终一致性。
- `archive-classification-scheme` 与 `archive-metadata` 对唯一性采用同一合同。

非目标：

- 不把 `scheme_id` 加入动态表名、档案主表或全文投影路由。
- 不重命名现有动态表，不增加迁移映射或兼容查询。
- 不支持分类编码改名或复用；确需迁移时必须另行设计档案、动态表和投影的完整迁移流程。
- 不改变分类方案、父子分类同方案、全宗可用分类范围等既有能力。
- 不顺带调整 Repository 与审计统一任务中的其他代码。

## 设计

### 业务不变量

分类方案继续负责组织分类节点和字段模板，但分类编码是跨方案稳定且不可变的路由业务键。任意两条历史分类不得使用相同 `category_code`；逻辑删除分类继续永久占用编码。

这一选择与现有档案主表和动态表命名保持一致：档案条目、案卷保存的 `category_code` 能持续反查同一分类，`category_code + item/volume + METADATA/PHYSICAL` 也持续定位同一物理表，无需让查询、档案记录或投影额外携带方案身份。

### 持久化约束

项目未发布，直接维护当前目标结构：将分类表的活动部分唯一索引从 `(scheme_id, category_code)` 调整为覆盖全部历史行的 `category_code` 普通唯一索引，不使用 `where deleted_flag = false`。

索引名称使用 `uk_am_archive_category_code` 表达永久全局唯一语义，不保留旧的方案内唯一索引、活动行部分索引或双重约束。

### Service 与 Repository

`ArchiveCategoryDataRepository` 保留一个明确操作注解标注的按分类编码查询方法，用于创建分类时提前识别未删除冲突，不新增通用 Repository helper。Hibernate `@SoftDelete` 会让该查询排除历史删除行；删除行冲突由数据库永久唯一索引识别。

`ArchiveCategoryService` 按以下顺序维护分类：

- 创建时校验编码非空且不超过 100 字符、名称非空且不超过 255 字符；存在未删除同编码分类时提前拒绝。
- 修改时先加载目标分类；请求编码与已保存编码不一致时以 `400 Bad Request / INVALID_ARGUMENT` 拒绝，不执行改名。
- 数据库唯一索引处理已删除历史编码冲突，以及并发请求在提前检查之后同时写入的竞争窗口。

提前检查和数据库永久唯一冲突都转换为 `409 Conflict` 的项目统一 ProblemDetail，错误码使用现有 `ALREADY_EXISTS` 映射。Service 只在异常链中的 Hibernate `ConstraintViolationException.constraintName` 等于 `uk_am_archive_category_code` 时转换；超长字段、外键失败和其他完整性异常继续原样抛出，不向客户端伪装成编码冲突。

### 规格校准

`archive-classification-scheme` 的“分类节点归属分类方案”改为分类编码在全部历史分类中永久唯一，同时保留父子分类必须同方案。`archive-metadata` 采用相同合同，并明确编码不可修改、逻辑删除不释放编码、重复创建返回 `409 / ALREADY_EXISTS`。

归档中的历史 delta 保持不变，用于记录当时设计；当前 `openspec/specs/` 是长期合同真相源。

## 失败路径

- 空白编码继续按现有参数校验拒绝。
- 超过 100 字符的编码或超过 255 字符的名称在写库前返回 `400 / INVALID_ARGUMENT`。
- 创建活动或历史分类已占用的编码返回 `409 Conflict`，并说明分类编码已存在。
- 修改分类时若请求编码不同于已保存编码，返回 `400 / INVALID_ARGUMENT`，且不写入。
- 并发创建绕过提前检查时，由永久唯一索引拒绝；仅该索引冲突转换为 `ALREADY_EXISTS` ProblemDetail。
- 其他 `DataIntegrityViolationException` 不转换为分类编码冲突。

## 测试与验证

- Service 单元测试覆盖创建重复编码、修改时保留自身编码、拒绝修改编码、输入长度校验、只转换指定约束名及透传其他完整性异常。
- 持久化或应用集成测试确认分类编码跨方案永久全局唯一，并确认逻辑删除后仍不可复用。
- 全局异常处理测试确认 `409 Conflict` 输出 `code=ALREADY_EXISTS` 的 ProblemDetail。
- OpenSpec 严格校验确认两个长期规格不再互相矛盾。
- 运行后端格式检查、编译和相关测试；最终运行后端全量测试。
- 使用模式断言确认当前迁移中不再存在 `uk_am_archive_category_scheme_code_active`、`uk_am_archive_category_code_active` 或分类编码部分唯一谓词，长期规格不再声明方案内唯一、编码可修改或删除后释放。

## 回滚边界

本变更不迁移生产数据。若实施验证失败，回滚本次规格、Service、Repository、测试和目标迁移修改即可；不需要动态表重命名或数据回填。已经存在的开发数据若包含跨方案重复编码，应重建数据库，不增加清洗兼容分支。
