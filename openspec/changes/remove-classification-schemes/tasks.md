## 1. 数据库与元数据合同

- [x] 1.1 重写 `V20260622_0100__create_archive_tables.sql` 和示例数据，删除分类方案表、分类 `scheme_id`、全宗分类 `default_flag` 及相关索引，并让示例全宗显式关联分类；验证：`cd backend/archive-server && mise exec -- mvn -Dtest=ArchiveMetadataServiceTests test` 退出码为 0，空库和示例数据初始化不存在分类方案或默认分类列。
- [x] 1.2 删除分类方案实体、Repository、Service、Controller、DTO，收敛分类和全宗分类范围请求响应；验证：`task server-format-check && task server-compile` 均退出码为 0，且 `rg 'ArchiveClassificationScheme|schemeId|scheme_id|defaultFlag|default_flag' backend/archive-server/src/main` 不返回分类方案或默认分类生产代码残留。

## 2. 服务端业务边界

- [x] 2.1 将分类服务改为全局树和显式全宗分类范围，空范围返回空集合，并增加窄的全宗分类可用性校验；验证：`cd backend/archive-server && mise exec -- mvn -Dtest=ArchiveMetadataServiceTests test` 退出码为 0，测试覆盖全局父子分类、空范围和未勾选分类拒绝。
- [x] 2.2 在条目创建/更新、案卷创建和导入预检/提交接入显式分类范围校验；验证：`cd backend/archive-server && mise exec -- mvn -Dtest=ArchiveItemFondsValidationTests,ArchiveVolumePermissionTests,ArchiveItemImportExportServiceTests test` 退出码为 0，未勾选分类时无档案、案卷或导入写入。

## 3. PC 前端

- [x] 3.1 删除分类方案前端类型和 API client，分类页面直接展示全局树并移除方案选择器、标签和表单字段；验证：`cd frontend && mise exec -- pnpm --filter @archive-management/web test -- ArchiveCategoriesPage.test.ts` 退出码为 0，页面不再出现“分类方案”且分类树仍可新增、编辑和选择。
- [x] 3.2 从全宗可用分类弹窗删除默认分类字段和复选框，保留显式分类与排序操作并补充组件测试；验证：`task frontend-check && task frontend-test` 均退出码为 0，保存请求不包含 `defaultFlag`，空集合可成功保存。

## 4. 真相源与最终验证

- [x] 4.1 更新 `CONTEXT.md`、数据库说明、用户手册和相关当前知识库，删除分类方案和默认分类表述；稳定规格中的旧合同由本 change 的 `REMOVED` 增量覆盖，归档时再合并；验证：生产代码与当前文档无旧概念残留，且 `task governance-check` 退出码为 0。
- [ ] 4.2 执行后端、前端和 OpenSpec 最终验证并在用户确认后归档 change；验证：`task server-test && task frontend-ready && task governance-check` 均退出码为 0，PostgreSQL 测试未跳过时覆盖目标结构和写入边界，工作树无验证生成的非预期文件。
