## 1. 接收记录与包解析

- [x] 1.1 新增接收包、生成条目关联表和固定 Jakarta Data 实体/Repository；运行 `task server-compile`，预期编译成功并生成实体元模型。
- [x] 1.2 实现 manifest-only ZIP 解析、大小/条目数/路径/重复项安全校验；运行 `cd backend/archive-server && mise exec -- mvn -Dtest=ArchiveIntakePackageParserTests test`，预期合法包通过且非法路径、额外文件和超限清单被拒绝。

## 2. 原子处理与 API

- [x] 2.1 增加分类编码和系统库角色的窄查询，完成旧版自定义接入包的事务闭环；该实现将在第 5 阶段按标准状态机替换。
- [x] 2.2 实现包上传、当前用户键集分页列表、详情 API，并更新兼容概览；运行 `cd backend/archive-server && mise exec -- mvn -Dtest=ArchiveIntakePackageControllerTests,IntakeServiceTests test`，预期 HTTP 合同、权限和用户隔离通过。

## 3. 归档接收工作台

- [x] 3.1 更新前端类型和 API client，支持 multipart 上传、列表与详情；运行 `task web-check`，预期 TypeScript 和 lint 检查通过。
- [x] 3.2 将归档接收页改为上传与历史工作台并覆盖加载、空、失败、提交和结果状态；运行 `cd frontend && mise exec -- pnpm --filter @archive-management/web test -- IntakePage`，预期上传、刷新、错误重试和状态展示测试通过。

## 4. 闭环验证

- [x] 4.1 执行 `task server-format && task server-test`，预期 Java 格式化完成且后端测试全部通过。
- [x] 4.2 执行 `task frontend-ready`，预期前端检查、测试和构建全部通过。
- [x] 4.3 执行 `task governance-check`，预期 OpenSpec strict 校验和治理脚本全部通过。
## 5. 完整离线信息包

- [x] 5.1 扩展接收包迁移、实体和记录事务，保存格式配置、原始 ZIP 存储对象、解析统计与四性检测结果；运行 `cd backend/archive-server && mise exec -- mvn -Dtest=ArchiveIntakePackageRecordServiceTests,ArchitectureRulesTest test`，预期原包与接收记录同事务提交或补偿回滚，检测结果可追溯。
- [x] 5.2 将解析器扩展为 DA/T 93—2022 附录 B“件”级目录树的流式临时文件解析，安全读取说明 TXT、目录 XML、档案元数据 XML 和内容数据，并执行路径、XML、关联、数量、大小及压缩炸弹校验；运行 `cd backend/archive-server && mise exec -- mvn -Dtest=ArchiveIntakePackageParserTests test`，预期合法标准结构通过，缺失标准文件、XXE、档号目录不一致、未归属文件和超限包被拒绝。
- [x] 5.3 按 DA/T 70—2018 保存自动通过、警告和人工/外部复核检测项；运行 `cd backend/archive-server && mise exec -- mvn -Dtest=ArchiveIntakePackageParserTests,ArchiveIntakePackageRecordServiceTests test`，预期结构、关联、数量和可用性结果可追踪，签名、病毒及实物载体项不被伪造为通过。
- [x] 5.4 实现 `PENDING_REVIEW` 人工复核、确认接收和退回状态机；确认接收时从原包重新解析，在整包事务内直接向 `HOLDING` 生成正式档案并挂接元数据 XML 和内容文件，不写入平行电子状态，失败时回滚数据库并触发对象存储补偿；运行 `cd backend/archive-server && mise exec -- mvn -Dtest=ArchiveIntakePackageServiceTests,ArchiveIntakePackageTransactionIntegrationTests test`，预期未复核不入库、接收全部入库、退回不入库、失败无部分条目或电子文件关系。
- [x] 5.5 增加原包下载短链、响应统计、四性检测报告、人工复核/交接表单和前端结果展示，更新标准格式说明与 API/页面测试；运行 `cd backend/archive-server && mise exec -- mvn -Dtest=ArchiveIntakePackageControllerTests test` 及 `cd frontend && mise exec -- pnpm --filter @archive-management/web test -- IntakePage intake.test.ts`，预期权限、用户隔离、下载、检测边界、确认接收、退回和文件统计通过。
- [x] 5.6 校准 repository role 生命周期合同，移除档案和案卷前端 `electronicStatus` 字段，并将工作台 `draftCount`/“草稿档案”改为 `intakeCount`/“预归档档案”；运行相关前端测试和 `task web-check`，预期类型、请求载荷、列表、表单和概览均不再暴露平行电子状态。
- [x] 5.7 在 0.0.1 阶段直接从初始建表和样例数据迁移中移除档案和案卷 `electronic_status`，同时移除实体、Mapper、命令/查询 API、导入模板、数据范围与运行时规则字段，并将工作台摘要改为按 `repository_role` 分别统计正式档案和预归档档案；运行服务端编译、相关集成测试和全量测试，预期全新数据库到 API 不再存在平行电子状态。

## 6. 完整验证与收口

- [x] 6.1 执行服务端格式检查与全量测试、前端检查与构建、前端单 worker 全量测试及 `task governance-check`；预期后端、前端、构建和 OpenSpec 治理全部通过，PostgreSQL 与对象存储补偿证据分别准确报告。
- [ ] 6.2 用户确认交付后，按 1.0.0 前治理规则将增量合同合入稳定 `intake` 规格并移除 active change；再次运行 `task governance-check`，预期严格校验和治理脚本全部通过。
