## 1. 建立 `close-archive-pc-mvp` OpenSpec 合同

- [x] 1.1 创建变更并读取 proposal、design、specs 和 tasks 制品指令
- [x] 1.2 编写 proposal 与 design，明确 PC MVP 范围、既有合同复用、服务端真相源和非目标
- [x] 1.3 编写 `archive-pc-mvp` 新能力规格和 `archive-record-search` 完整 delta，覆盖分页、生命周期、字段、案卷、关系、明细、权限、摘要和错误恢复场景
- [x] 1.4 运行 `openspec validate close-archive-pc-mvp --strict` 并修复全部错误和 warning

## 2. 档案搜索与管理接入游标分页

- [ ] 2.1 编写并运行 `useArchiveItemSearch`、档案管理页和档案搜索页定向失败测试，确认因已提交查询、上一页/下一页、页大小、加载或原位错误行为缺失而失败
- [ ] 2.2 实现管理页显式保存 `limit`、当前游标、响应导航游标、已提交查询和加载错误，翻页与刷新不读取未提交草稿
- [ ] 2.3 在全文搜索页接入现有 `CursorPagination`，保持其查询语义独立，不抽取参数化通用查询框架
- [ ] 2.4 使用超过 100 条且排序值重复的数据验证稳定翻页无重复、无遗漏，并运行相关前端定向测试和 `pnpm test`

## 3. 补齐删除、锁定和解锁操作

- [ ] 3.1 编写并运行档案删除 API、锁定原因、解锁确认、删除确认、权限与刷新当前查询定向失败测试，确认因目标行为缺失而失败
- [ ] 3.2 在共享档案客户端和生命周期组合式函数中接通资源 `DELETE`、`:lock` 与 `:unlock`，保持服务端 ProblemDetail 原因
- [ ] 3.3 新增行操作组件并按档案状态、权限和进行中动作显示或禁用锁定、解锁、删除、电子文件和审计入口
- [ ] 3.4 运行 API、生命周期和档案管理页面定向测试，确认成功刷新已提交查询且失败保留上下文

## 4. 编辑物理字段、密级和保管期限

- [ ] 4.1 编写并运行编辑回填、只读详情、参考数据选择、实物字段与动态字段提交定向失败测试，确认因目标行为缺失而失败
- [ ] 4.2 扩充档案编辑状态，加载启用密级与保管期限，并按现有元数据类型渲染 `physicalFields` 和 `dynamicFields`
- [ ] 4.3 保存时分别规范化并提交密级、保管期限、实物字段和动态字段，移除固定空 `physicalFields`
- [ ] 4.4 接入 `fieldViolations` 表单回填并运行档案编辑器、管理页和动态字段定向测试

## 5. 开发案卷管理 PC 页面

- [ ] 5.1 编写并运行 `ArchiveVolumeDataRepository`、案卷 Service/Controller、档案 `volumeId` Mapper 与前端客户端定向失败测试，确认因案卷游标分页、`volumeId` 筛选或 `:addItem` 精确合同缺失而失败
- [ ] 5.2 为固定实体 `ArchiveVolume` 新增 `ArchiveVolumeDataRepository`，使用 `Restriction<ArchiveVolume>`、`PageRequest`、稳定 `Order(createdAt DESC, id DESC)` 和 `CursoredPage<ArchiveVolume>` 完成列表查询；Service 在事务内转换为项目 `CursorPageResponse<ArchiveVolumeResponse>`，不暴露 provider 页类型，并补齐 Repository、Service、Controller 与 ArchUnit 验证。仅迁移案卷列表，创建、详情和唯一性查询保留现有 MyBatis，不修改 `ArchiveMapper.xml` 中的案卷列表 SQL
- [ ] 5.3 给 `SearchArchiveItemsRequest`、查询 Service 与 MyBatis criteria/XML 增加可空 `volumeId`，纳入业务筛选和 cursor 查询摘要并补齐 Controller/Service/Mapper 测试
- [ ] 5.4 实现案卷前端类型与客户端：列表使用 cursor 合同，`:addItem` 提交 `{ itemId, displayOrder? }` 并以 `Promise<void>` 处理 `204 No Content`
- [ ] 5.5 编写案卷页面和路由定向测试并立即运行，确认因页面、Drawer、游标交互或 `/archive/volumes` 权限路由尚未实现而失败
- [ ] 5.6 最小实现案卷管理页面、编辑/卷内档案 Drawer 和 `/archive/volumes` 路由，支持按全宗和分类筛选、创建、查看、游标翻页、加入档案及 `archive:item:read` 权限元数据
- [ ] 5.7 重新运行案卷 Repository、Service、Controller、ArchUnit、客户端、页面和路由测试，确认全部转绿

## 6. 开发档案关系维护

- [ ] 6.1 编写并运行关系 Controller/Service/Mapper、前端客户端和关系 Drawer 定向失败测试，确认因关系列表游标分页或维护行为缺失而失败
- [ ] 6.2 将 `GET /api/v1/archive-items/{archiveItem}/relations` 改为 `CursorPageResponse<ArchiveItemRelationResponse>`；扩展现有 MyBatis Mapper，在同一分页 SQL 中连接目标档案摘要、应用 `ArchiveDataScopeFilter` SQL 分组和 `id ASC` cursor 边界，`depth` 进入查询摘要。禁止分页后由 Repository/Service 逐行过滤，不新增平行 Repository、adapter 或分页状态源
- [ ] 6.3 接通关系前端 cursor 客户端，并用分类、关键字和游标搜索选择目标档案，禁止手工输入 ID 和选择当前档案
- [ ] 6.4 在档案资源 Drawer 增加关系页签与现有 cursor 组件，读取使用 `archive:item:read`，创建与删除使用 `archive:item:update`
- [ ] 6.5 运行关系后端和前端定向测试，验证自关联、重复或越权冲突，成功后只刷新关系页签

## 7. 开发明细表定义 PC 配置

- [ ] 7.1 编写并运行明细表、字段、`:build` 客户端及配置面板定向失败测试，确认因目标行为缺失而失败
- [ ] 7.2 在分类配置页新增紧凑明细表面板，维护现有字段类型、编码、名称、列名、精确检索和排序
- [ ] 7.3 使用 `archive:metadata:manage` 控制入口并保留服务端授权，构建失败时保留定义和可重试状态
- [ ] 7.4 运行明细表客户端、配置面板和分类页面定向测试，确认未引入通用设计器

## 8. 增加明细行 CRUD 与档案编辑

- [ ] 8.1 编写并运行明细行 Service 和 Controller 定向失败测试，确认因权限、数据范围、分类归属、锁定状态、PATCH 出现性语义、路径资源一致性或逻辑删除行为缺失而失败
- [ ] 8.2 实现明细行查询、创建、PATCH、删除 Service 与完整 Controller 路径；列表使用 `CursorPageResponse`、URL query 中的 `limit`/`cursor` 和 `lineOrder ASC, id ASC`
- [ ] 8.3 使用 Jackson `JsonNode` 边界或等价出现性表示构造 `PatchArchiveItemLineRowRequest`：`values` 缺失键不修改、显式 null 清空，`lineOrder` 缺失不修改、显式 null 返回 `INVALID_ARGUMENT`
- [ ] 8.4 在 MyBatis 中实现白名单动态表和列映射、参数绑定、部分字段更新与逻辑删除，禁止请求标识符直接拼接 SQL
- [ ] 8.5 编写并运行明细行前端客户端和编辑组件定向失败测试，确认因新增、PATCH、删除或只读行为缺失而失败，再在档案编辑器中完成对应交互
- [ ] 8.6 运行明细行后端 Service/Controller 与前端客户端/组件定向测试，并执行同次 Java 变更的 Spotless 格式化

## 9. 统一路由、菜单和按钮权限

- [ ] 9.1 编写并运行 `superAdmin` 全权限、普通用户权限码、空菜单分组和无权直接访问定向失败测试，确认因目标行为缺失而失败
- [ ] 9.2 在权限 Store 实现唯一 `has(code)` 判断，并让路由、菜单、分组、页签和按钮复用该判断
- [ ] 9.3 为档案搜索、管理、案卷、元数据、治理和系统页面补齐权限元数据，隐藏本阶段尚未完成的占位入口
- [ ] 9.4 新增统一无权限页和导航守卫，运行权限 Store、路由及菜单组件测试并确认服务端授权仍生效

## 10. 用数据范围内真实统计替换工作台假数据

- [ ] 10.1 编写并运行工作台摘要 Service 和 Controller 定向失败测试，确认因启用分类汇总、数据范围、逻辑删除或空范围全零行为缺失而失败
- [ ] 10.2 在独立 Spring Bean 中复用档案查询条件和数据范围编译，使用 MyBatis 聚合档案、草稿、锁定和电子文件数量
- [ ] 10.3 暴露 `GET /api/v1/workspace-summary` 和 `WorkspaceSummaryResponse`，补充 Controller 合同与 ProblemDetail 测试
- [ ] 10.4 编写并运行工作台客户端和页面定向失败测试，确认因真实摘要或移除虚构事项行为缺失而失败，再替换硬编码统计并删除演示待办和虚构近期事项
- [ ] 10.5 运行工作台后端和前端定向测试，确认摘要失败不阻断其他工作区导航

## 11. 统一关键页面原位错误恢复

- [ ] 11.1 编写并运行无请求状态的 `RequestErrorState` 定向失败测试，确认因错误消息、可访问重试动作或进行中禁用行为缺失而失败
- [ ] 11.2 实现只负责展示与发出重试事件的共享组件，不在组件中引入第二份请求状态
- [ ] 11.3 在档案搜索、档案管理、案卷和工作台接入原位错误状态，保留查询、结果与编辑输入，并对游标失效从相同查询第一页恢复
- [ ] 11.4 运行共享组件和全部接入页面测试，确认瞬时命令继续使用消息反馈且字段错误回填表单

## 12. 完成阶段验证与文档回填

- [ ] 12.1 在 `server/` 运行 Spotless 和完整 Maven 测试，记录零失败、零错误结果及任何数据库依赖
- [ ] 12.2 在仓库根目录运行 `pnpm check`、`pnpm test`、`pnpm run build` 和必要的 Vite+ task
- [ ] 12.3 运行 `openspec validate close-archive-pc-mvp --strict`、`openspec validate --all --strict` 和 `pnpm run check:source-lines`
- [ ] 12.4 更新 MVP 差距、API 和架构文档，只对已有实现与验证证据的任务勾选完成
- [ ] 12.5 逐项审计超过 100 条翻页、生命周期、完整字段、案卷、关系、明细、权限、真实摘要和原位重试的代码、测试与页面入口证据
