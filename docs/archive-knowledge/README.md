# 档案行业规范知识库

本目录用于沉淀档案行业法规、国家标准、行业标准和专题规范对本项目的理解。它不是标准原文仓库，也不是强制实现合同。

## 使用边界

- 只保存题录、官方链接、摘要理解、项目影响和采纳建议，不复制受版权保护的标准正文。
- 业务字段、状态机、接口行为、权限边界、流程验收和兼容性要求，必须进入对应 `openspec/specs/*` 后才作为实现合同。
- 协作方式、架构边界、工具链和代码风格规则才进入 `AGENTS.md`。
- 与当前 OpenSpec 或真实实现冲突的外部条款，先记录为“差异与待确认”，不要直接改代码。
- 标准状态、发布日期和适用性可能变化；做正式采纳前必须重新核对官方来源。

## 文档索引

- [标准目录](./standards-catalog.md)：首批法规、国家标准、行业标准和专题标准清单。
- [档案专业术语](./professional-terminology.md)：档案工作、整理、著录、档号和电子档案常用术语。
- [常用档案分类](./classification-reference.md)：机关、高校和通用业务系统常见门类、分类层级和项目建模建议。
- [档号与全宗规则](./reference-code-and-fonds-rules.md)：全宗设置、档号组成、编号模板和唯一性边界。
- [高校与档案馆实践](./archive-institution-practices.md)：国内高校、国外大学档案馆和综合档案馆的公开管理做法。
- [档案系统能力地图](./archive-system-capability-map.md)：完整能力、基础能力、最小 POC 和 MVP 分层。
- [法规底座](./legal-and-regulatory-baseline.md)：档案法、实施条例和机关档案管理规则对系统边界的影响。
- [电子档案系统能力](./electronic-archive-system.md)：电子文件管理系统、电子档案管理系统的功能视角。
- [归档、移交与单套管理](./filing-transfer-and-single-copy.md)：归档、接收、移交、单套管理的流程理解。
- [元数据、封装与格式](./metadata-package-and-format.md)：元数据、XML 封装、长期保存格式对模型和存储的影响。
- [四性、证据效力与备份](./authenticity-integrity-usability.md)：真实性、完整性、可用性、安全性、证据效力和备份。
- [会计与 ERP 专题](./domain-accounting-and-erp.md)：电子会计档案、财务报销电子凭证和 ERP 归档专题。

## 推荐阅读顺序

1. 先读 [标准目录](./standards-catalog.md)，确认相关标准和采纳优先级。
2. 再读 [档案专业术语](./professional-terminology.md)，统一“全宗、案卷、件、档号、著录、归档”等基础语言。
3. 读 [常用档案分类](./classification-reference.md) 和 [档号与全宗规则](./reference-code-and-fonds-rules.md)，明确分类、全宗、档号如何落到模型。
4. 读 [档案系统能力地图](./archive-system-capability-map.md)，确认当前讨论处在 POC、MVP、基础能力还是完整能力层。
5. 做通用档案能力时读法规底座、系统能力、归档移交、元数据封装和四性文档。
6. 做高校、档案馆或标杆调研时读 [高校与档案馆实践](./archive-institution-practices.md)。
7. 做会计、财务报销、ERP 或业务系统归档时，再读专题文档。
8. 需要落地实现时，把已确认要求迁入 OpenSpec，再改代码和测试。

## 当前项目优先关注

- 档案分类、全宗、档案条目、案卷、卷内文件和关联档案的业务模型。
- 电子文件归档、档案移交接收、单套管理和长期保存能力。
- 元数据、动态字段、全文检索、对象存储、审计日志和权限控制。
- 四性检测、证据效力维护、备份恢复和导入导出验收。
- 会计档案、财务报销电子凭证、ERP 系统电子文件归档等专题能力。
