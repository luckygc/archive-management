## 1. 架构约束

- [x] 1.1 在 AGENTS.md 和架构文档中记录单实现业务类与 Spring Bean public 自调用规则
- [x] 1.2 以 TDD 增加 Spring Bean public 自调用 ArchUnit 检查并输出完整现有违规清单
- [x] 1.3 以 TDD 增加单实现 Service/Manager 接口 ArchUnit 检查和明确例外

## 2. Spring Bean public 自调用治理

- [x] 2.1 修复元数据模块 Bean 的 public 自调用并通过聚焦测试
- [x] 2.2 修复档案记录模块 Bean 的 public 自调用并通过聚焦测试
- [x] 2.3 修复认证、授权、组织、存储及基础设施 Bean 的剩余 public 自调用
- [x] 2.4 运行完整架构测试确认当前生产代码无 public 自调用违规

## 3. 业务接口与模块入口治理

- [x] 3.1 清点业务 Service/Manager 接口并删除无明确例外的单实现接口
- [ ] 3.2 按业务闭环迁移元数据调用方并删除仅转发的总门面入口
- [ ] 3.3 按查询、写入状态和关系用例收缩 ArchiveItemRoutingService
- [ ] 3.4 更新相关 Controller、跨模块调用、测试与 ArchUnit 包边界

## 4. 审计字段唯一来源

- [x] 4.1 强化无状态 Repository insert/update 用户审计集成测试并验证测试有效性
- [x] 4.2 删除被拦截器覆盖的固定实体手工 createdBy/updatedBy 赋值
- [x] 4.3 保留并记录 MyBatis、业务操作人和非拦截器路径的显式审计例外

## 5. 前端共享代码精简

- [x] 5.1 删除 ArchiveItemManagementPage 的重复 errorMessage 实现并使用 frontend-core
- [ ] 5.2 按元数据、档案记录、治理、本体和规则拆分档案 API 与类型
- [ ] 5.3 按认证、授权和组织拆分非档案 API 与类型
- [ ] 5.4 更新页面导入并删除旧 archive.ts 聚合文件

## 6. 前端热点页面治理

- [ ] 6.1 按查询、编辑、电子文件和审计业务区域收缩档案管理页面
- [ ] 6.2 按方案、版本、适用范围和绑定业务区域收缩治理页面
- [ ] 6.3 检查其余大页面，只提取已有稳定业务边界的组件

## 7. 验证与收尾

- [ ] 7.1 运行后端 Spotless、编译、架构测试和完整测试
- [ ] 7.2 运行前端 pnpm check、pnpm test 和 pnpm build
- [ ] 7.3 按 code-maintainability 规格逐项审计当前源码并更新架构文档
- [ ] 7.4 验证 OpenSpec 变更并确认全部任务完成
