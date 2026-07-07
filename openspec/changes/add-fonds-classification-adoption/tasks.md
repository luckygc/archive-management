## 1. OpenSpec

- [x] 1.1 校验 `add-fonds-classification-adoption` OpenSpec 变更。

## 2. 数据模型与基线结构

- [x] 2.1 在档案元数据基线 DDL 中新增分类方案、全宗可用分类范围表，并为 `am_archive_category` 增加 `scheme_id`。
- [x] 2.2 新增 `ArchiveClassificationScheme` 和 `ArchiveFondsCategoryScope` 实体。
- [x] 2.3 新增对应 Jakarta Data Repository，并保持固定表 CRUD 走 Repository。

## 3. 后端服务与 API

- [x] 3.1 先补 `ArchiveMetadataServiceTests`，覆盖分类方案创建、分类归属方案、父子同方案校验和全宗可用分类解析。
- [x] 3.2 实现分类方案列表、创建和更新服务方法。
- [x] 3.3 调整分类创建/更新，要求分类方案 ID 并校验父级同方案。
- [x] 3.4 实现保存/查询全宗可用分类范围。
- [x] 3.5 实现按全宗编码查询可用分类，未维护分类范围时回退默认分类方案。
- [x] 3.6 在 `ArchiveMetadataController` 暴露分类方案、全宗可用分类范围和按全宗分类查询 API。
- [x] 3.7 运行后端相关测试。

## 4. 前端管理入口

- [x] 4.1 先补分类页面测试，覆盖分类方案展示、分类创建选择方案和全宗可用分类范围入口。
- [x] 4.2 补齐前端类型和 API：分类方案、全宗可用分类范围、按全宗分类查询。
- [x] 4.3 改造分类管理页面，增加分类方案列表和分类方案选择。
- [x] 4.4 在全宗管理或分类管理页面提供全宗可用分类范围保存入口。
- [x] 4.5 运行前端相关测试。

## 5. 验证收口

- [x] 5.1 运行 `openspec validate add-fonds-classification-adoption --strict`。
- [x] 5.2 运行后端 Maven 编译或相关测试。
- [x] 5.3 运行前端 `vp check` 和 `vp test`。
- [x] 5.4 运行 `git diff --check` 并检查没有无关改动。
