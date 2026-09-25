## 1. 依赖与共享组件

- [x] 1.1 在前端 catalog 和管理端包中加入稳定版 `@tanstack/vue-table` v9；运行 `cd frontend && mise exec -- pnpm install --frozen-lockfile`，预期依赖解析成功且 lockfile 无漂移。
- [x] 1.2 实现统一数据表格的列类型、语义化渲染、加载、空状态、横向滚动、固定列和命名插槽；运行 `cd frontend && mise exec -- pnpm --filter @archive-management/web exec vp test run src/shared/components/data-table/AmDataTable.test.ts`，预期共享组件测试全部通过。
- [x] 1.3 实现本地/远程多列排序、优先级提示、组合键和树形行；再次运行 `cd frontend && mise exec -- pnpm --filter @archive-management/web exec vp test run src/shared/components/data-table/AmDataTable.test.ts`，预期多列排序、远程事件和树形场景全部通过。

## 2. 业务表格迁移

- [x] 2.1 将档案检索结果迁移到统一表格并把完整排序状态映射为既有 `orderBy`；运行 `cd frontend && mise exec -- pnpm --filter @archive-management/web exec vp test run src/pages/archive-library/ArchiveResultTable.test.ts src/pages/archive-library/ArchiveLibraryPage.test.ts`，预期多列排序顺序和清空 cursor 场景通过。
- [x] 2.2 迁移完整内存集合表格，保留业务插槽、按钮和本地排序；运行 `cd frontend && mise exec -- pnpm --filter @archive-management/web run test`，预期现有页面测试与新增迁移测试全部通过。
- [x] 2.3 迁移 cursor/offset 分页、抽屉明细及组织部门树形表格；运行 `! rg -n '<el-table|<el-table-column|\bElTable\b|\bElTableColumn\b' frontend/admin/src --glob '*.{vue,ts}'`，预期源码中无 Element Plus 表格引用。

## 3. 验证与收尾

- [x] 3.1 更新自动生成的组件声明和迁移影响的测试快照或选择器；运行 `mise run frontend-ready`，预期前端检查、测试和构建全部通过。
- [ ] 3.2 校验 OpenSpec 和仓库治理规则；运行 `openspec validate replace-element-table-with-tanstack-v9 --strict && mise run governance-check`，预期变更规格与治理检查全部通过。
