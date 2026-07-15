## 1. OpenSpec 与 API 合同

- [x] 1.1 校验 `improve-archive-governance-workbench` OpenSpec 变更。

## 2. 后端治理读取接口

- [x] 2.1 先补治理服务测试，覆盖按版本读取适用范围和装配绑定。
- [x] 2.2 实现 `ArchiveGovernanceService` 的适用范围和装配绑定读取方法。
- [x] 2.3 在 `ArchiveGovernanceController` 新增两个 `GET` 子资源接口。
- [x] 2.4 运行治理服务相关测试，确认新增读取能力通过。

## 3. 前端治理版本工作台

- [x] 3.1 先补前端页面测试，覆盖选中版本后展示适用范围、装配绑定和默认解析试算入口。
- [x] 3.2 补齐前端治理范围、治理绑定响应类型和读取 API，修正覆盖保存响应类型。
- [x] 3.3 改造治理方案页面，增加版本选中态、版本概览、适用范围表单表格、装配绑定表单表格和默认解析试算。
- [x] 3.4 运行前端治理页面测试，确认页面工作台行为通过。

## 4. 验证收口

- [x] 4.1 运行 `openspec validate improve-archive-governance-workbench --strict`。
- [x] 4.2 运行后端 Maven 相关测试或编译。
- [x] 4.3 运行前端 `vp check` 和相关测试。
- [x] 4.4 检查 diff，确认没有无关重构或夹带改动。
