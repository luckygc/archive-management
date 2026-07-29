## 1. 数据模型与迁移

- [x] 1.1 新增虚拟业务库、库变更历史、真实库房、存放位置、实物对象和位置历史表，并为条目、案卷增加当前业务库；运行 `cd backend/archive-server && mise exec -- mvn -Dtest=ServerApplicationTests test`，预期 Docker 可用时 Flyway 迁移成功且应用上下文启动，Docker 不可用时测试明确跳过。
- [x] 1.2 新增固定实体与窄 Jakarta Data Repository，并更新实体软删除架构清单；运行 `cd backend/archive-server && mise exec -- mvn -Dtest=ArchitectureRulesTest test`，预期所有持久化与包边界规则通过。

## 2. 业务库能力

- [x] 2.1 实现业务库配置 Service、Controller 和合同类型；运行 `cd backend/archive-server && mise exec -- mvn -Dtest=ArchiveRepositoryServiceTests test`，预期覆盖创建、禁用目标拒绝和删除引用冲突。
- [x] 2.2 为条目与案卷创建、详情响应和库变更动作接入当前业务库及变更历史；运行 `cd backend/archive-server && mise exec -- mvn -Dtest=ArchiveRepositoryAssignmentServiceTests test`，预期条目/案卷库变更保持 ID 并写历史。
- [x] 2.3 在正式条目搜索和案卷列表中应用 `HOLDING` 库过滤；运行 `cd backend/archive-server && mise exec -- mvn -Dtest=ArchiveRepositoryQueryIsolationTests test`，预期预归档库和移交库记录不进入正式查询。

## 3. 实物与位置能力

- [x] 3.1 实现实物对象创建、读取、修改和逻辑删除；运行 `cd backend/archive-server && mise exec -- mvn -Dtest=ArchivePhysicalObjectServiceTests test`，预期所有者互斥、首期一档一实物和实物存在性规则通过。
- [x] 3.2 实现真实库房、存放位置配置及层级校验；运行 `cd backend/archive-server && mise exec -- mvn -Dtest=ArchiveStorageLocationServiceTests test`，预期虚拟业务库与库房独立，跨库房父子、自引用、成环和在用删除均被拒绝。
- [x] 3.3 实现实物批量关联位置和追加式位置历史；运行 `cd backend/archive-server && mise exec -- mvn -Dtest=ArchivePhysicalLocationServiceTests test`，预期批量操作全成全败、无变化不写历史、历史倒序返回。

## 4. 契约与回归验证

- [x] 4.1 补充 Controller/API 合同测试并核对错误响应；运行 `cd backend/archive-server && mise exec -- mvn -Dtest='*ArchiveRepository*Tests,*ArchivePhysical*Tests,*ArchiveStorageLocation*Tests' test`，预期新增 API 测试全部通过。
- [x] 4.2 收紧集合响应边界，并将一档一实物查询改为单资源接口；运行 `cd backend/archive-server && mise exec -- mvn -Dtest='ArchiveItemElectronicFileServiceTests,ArchiveItemElectronicFileControllerTests,ArchivePhysicalObjectServiceTests,ArchivePhysicalObjectControllerTests' test`，预期 Service 不依赖 HTTP 集合包装且单资源不存在时返回 404。
- [ ] 4.3 执行格式、后端回归和 OpenSpec 治理验证；依次运行 `task server-format`、`task server-test`、`task governance-check`，预期格式化完成、后端测试通过且 OpenSpec 严格校验通过。
