# 高校与档案馆实践

本页记录公开资料中较有参考价值的高校和档案馆管理方式。它们是标杆输入，不是当前项目默认合同。

## 国内高校实践

### 高校通用制度

高校档案管理通常围绕教学、科研、管理、基建、设备、出版、外事、财会、声像等学校活动形成材料。公开的《高等学校档案实体分类法》强调分类、编号、排架、检索的统一，同时保留各校结合规模和历史调整类目的空间。

项目启发：

- 高校场景应内置或可配置高校分类模板，而不是只提供机关文书模板。
- 教学、科研、基建、设备、财会、人事和声像的字段差异很大，应支持专题字段集合。
- 学籍、成绩、论文、人事、财务等分类需要更严格的数据权限。

### 北京大学档案馆

北京大学档案馆公开页面显示，其馆藏按多个全宗组织，包括北京大学全宗、国立西南联合大学全宗、国立北平大学全宗、日伪占领区的“国立北京大学”全宗、燕京大学全宗等。

项目启发：

- 高校档案系统必须支持多全宗和历史沿革。
- 同一个现代学校可能管理多个历史主体形成的档案。
- 全宗不能简单等同当前学校组织机构。

### 清华大学档案馆

清华大学档案馆公开介绍中，档案馆下设多个业务部门，并与校内单位合作设立分室；馆藏覆盖历史时期文书档案、学生学籍注册卡和成绩单、科研和工程项目档案、建筑基建档案和图纸、研究生论文档案、人事档案、校友实物档案等。

清华音像档案管理办法还公开了更细的音像档号结构，按全宗号、摄录年度、门类、单位编码、组号、流水号组成，并要求音像档号与音像一一对应。

项目启发：

- 大学校内可能采用“档案馆集中管理 + 部门分室/责任单位归档”的组织模式。
- 照片、录像、录音不适合只按普通附件处理，需要按活动分组、逐件编号和编目。
- 档号规则可按门类不同而不同。

### 西北工业大学档案馆

西北工业大学档案馆公开馆藏简介显示，其馆藏存在西北工学院、西安航空学院、西北工业大学三个全宗，并提供案卷目录、卷内目录、文件级卡片、专题卡片、文件尾号表、编研材料等多种检索工具。

项目启发：

- 合并、迁校、改名历史会直接影响全宗设置。
- 传统档案检索不是只有一个搜索框，目录、卷内目录、专题卡片等都对应不同检索入口。
- 系统需要能同时支持案卷级、文件级和专题级检索。

## 国外大学实践

### Harvard University Archives

Harvard University Archives 同时提供 University Archives 和 Records Management。公开页面强调 records schedules 用于帮助员工识别记录类型、判断保存期限，以及在不再需要时决定销毁还是移交档案馆。

项目启发：

- 记录管理和档案管理应衔接：不是所有业务记录都永久保存。
- records schedule 相当于“分类 + 保管期限 + 处置动作”的制度化规则。
- 系统后续可把“保管期限表”和“归档范围”做成可配置对象。

### University of Cambridge Archives

Cambridge University Archives 负责选择、移交和保存学校内部行政记录，并提供行政和研究利用。公开说明中，移交前需要先联系档案馆，由档案馆评估材料是否适合加入馆藏、估算规模；移交后进行编目、保护并在阅览室监督下提供利用，部分新进馆藏会因信息合规要求限制访问。

项目启发：

- 移交接收需要鉴定和评估，不是文件上传即入馆。
- 接收后应有编目、保管、利用和限制开放状态。
- 访问控制要能表达“可保存但暂不开放”。

### University of Oxford

Oxford 公开 retention schedules，按学生记录、人事、财务等类型提供保存期限和处理依据，强调数据保存不应超过收集目的所需时间。

项目启发：

- 高校档案和个人数据治理有交叉，保管期限与隐私合规不能分开设计。
- 学生、人事、财务分类应有独立保管期限和访问规则。
- 销毁、匿名化、限制开放和永久保存都可能是处置结果。

### Stanford University

Stanford 的学生记录保管政策将 Registrar、degree programs 等职责区分开，并对研究生记录、申请材料、学籍和访问权作出不同处理。

项目启发：

- 同一类“学生档案”内部仍有不同责任主体和保管规则。
- 中央系统和院系本地材料可能并存，归档系统应记录来源和责任单位。
- 学生本人访问权、隐私、敏感材料隔离需要进入权限模型。

## 综合档案馆和国际做法的共性

| 共性                             | 项目启发                                                     |
| -------------------------------- | ------------------------------------------------------------ |
| 先有 records schedule 或归档范围 | 系统需要表达哪些材料应归档、保留多久、到期如何处置           |
| 移交前有鉴定和评估               | 正式移交应建模申请、评估、接收、问题反馈和回执               |
| 编目和 finding aid 很重要        | 著录、目录、检索入口和全文索引要分层                         |
| 来源原则长期有效                 | 全宗、形成单位、责任主体和历史沿革要可追踪                   |
| 访问限制常态存在                 | 开放、限制开放、涉密、个人信息、版权和校内权限要分开         |
| 多载体共存                       | 纸质、电子、照片、音视频、实物、网页和数据归档需要不同元数据 |

## 对当前项目的优先建议

1. 增加“分类方案”和“档号规则”概念，不把分类、全宗、档号写死。
2. 全宗、分类、保管期限、档号、形成单位、责任单位、开放状态应成为核心档案管理字段。
3. 高校场景应支持教学、科研、基建、设备、财会、人事、声像、论文等专题字段。
4. 归档范围和保管期限表应可配置，并能影响归档校验、到期提醒和处置动作。
5. 照片、录音、录像应支持按活动组管理，不只作为普通附件。
6. 移交接收应支持批次、清单、检测、问题反馈和回执。
7. 利用访问应支持阅览、下载、复制、导出和限制开放等不同权限。

## 待进入 OpenSpec 的决策

- 是否把高校分类模板作为内置初始化数据。
- 是否把 records schedule / 保管期限表作为第一阶段能力。
- 是否支持多全宗和全宗沿革。
- 是否为照片、录音、录像建立专题模型。
- 是否支持移交前鉴定和移交后限制开放。

## 公开来源

- [北京大学档案馆](https://www.dag.pku.edu.cn/)
- [北京大学档案馆历史沿革](https://www.dag.pku.edu.cn/summary.html)
- [清华大学档案馆本馆简介](https://dag.tsinghua.edu.cn/gqgy/bgjj.htm)
- [清华大学音像档案管理实施办法](https://dag.tsinghua.edu.cn/info/1056/3378.htm)
- [西北工业大学档案馆馆藏简介](https://dag.nwpu.edu.cn/info/1253/3420.htm)
- [西北工业大学科研项目档案归档范围及要求](https://dag.nwpu.edu.cn/info/1247/4607.htm)
- [Harvard University Archives: Records Schedules](https://library.harvard.edu/libraries/harvard-university-archives/records-management/records-schedules)
- [Harvard University Archives: Transfer Your Records](https://library.harvard.edu/libraries/harvard-university-archives/records-management/transfer-your-records)
- [University of Cambridge Records Management FAQs](https://www.information-compliance.admin.cam.ac.uk/records-management/records-management-faqs)
- [University of Cambridge Statement of Records Management Practice and Master Records Retention Schedule](https://www.information-compliance.admin.cam.ac.uk/files/records_statement_and_retention_schedule.pdf)
- [University of Oxford Retention Schedules](https://compliance.admin.ox.ac.uk/retention-schedules)
- [Stanford Retention of Student Records](https://gap.stanford.edu/handbooks/gap-handbook/chapter-8/subchapter-3/page-8-3-1)
- [Stanford Archives: Planning Transfers and Material Donations](https://guides.library.stanford.edu/university-archives/how-to-manage-materials)
