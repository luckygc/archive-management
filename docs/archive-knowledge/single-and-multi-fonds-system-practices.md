# 单全宗与多全宗系统实践调研

## 调研问题与边界

本页调查其他档案系统如何处理“多数单位只有一个全宗、少数大型企业或档案馆管理多个全宗”的差异，重点回答：

1. 全宗是否是独立顶层实体；
2. 一个部署是否支持多个全宗或多个档案资源；
3. 单全宗场景是否自动选择、隐藏选择器或仍显式操作；
4. 机构、资源库、全宗和下级著录层级如何关联；
5. 对本项目可借鉴什么。

资料优先采用产品官方文档、官方源码、国家档案局资料、政府采购文件和采购单位公告。公开资料只能证明其明确记载的模型或交互；没有公开操作手册或源码佐证的行为不作推断。

本页是外部实践研究，不是当前产品合同。业务规则仍以 `openspec/specs/` 和活动 change 为准。

## 结论摘要

没有一种行业通用的界面模式，但可以看到两条稳定路径：

- **档案馆、联合目录和集团平台按多全宗设计。** 系统保留机构或资源库边界，并允许其下存在多个顶层档案资源；多全宗时通过当前资源库、目录树、筛选或作用域切换来操作。
- **单一机关或小型档案室弱化全宗操作。** 中国现行机关档案规则本来就规定一个机关的全部档案构成一个全宗；公开采购案例中甚至直接省略“全宗管理”模块。AtoM 则保留数据关系，但可隐藏单一资源库造成的重复展示。

需要特别注意：AtoM 隐藏的是单一 **repository** 的重复信息，不是检测唯一 **fonds** 后自动选中；ArchivesSpace 的全局上下文同样是 Repository，不是 Resource/Fonds。两者都没有“当前全宗”全局选择器，也没有公开证据证明会按全宗数量自动进入单全宗模式。它们对全宗层级的主要降噪方式，是让下级记录继承当前著录树上下文。

对本项目最重要的启发不是删除多全宗模型，而是把**能力上限**和**默认交互**分开：

- 模型和服务端继续支持 `机构 1 → N 全宗`，全宗边界显式、可校验；
- 用户只有一个可用全宗时，业务操作自动进入该上下文，不要求重复选择；
- 页面仍以低干扰方式显示当前全宗，避免数据归属不可见；
- 用户有多个可用全宗时，再显示持久的上下文切换器、筛选和跨全宗入口；
- 不应把“全宗”和“机构/资源库”合并成同一概念。

## 对比表

| 系统或资料 | 全宗/资源的模型 | 单部署多全宗或多资源 | 单一场景交互 | 机构、资源库、全宗关系 |
| --- | --- | --- | --- | --- |
| AtoM 2.10 | 没有独立 `Fonds` 实体；`fonds` 是档案著录记录的一个层级，顶层记录也可以是 collection、series 等 | 支持多个 archival institution/repository，每个 repository 可关联多个顶层档案著录 | 管理员把 `Multiple repositories` 设为 `No` 后，系统隐藏资源库筛选、高级搜索资源库条件和结果中的重复资源库名称；公开文档未证明会按资源库数量自动切换该设置 | archival institution/repository 持有多个顶层 archival description；repository 只需链接到顶层描述，下级自动继承 |
| ArchivesSpace | 没有独立 `Fonds` 实体；`Resource` 是著录树根，`Resource.level` 可为 fonds、collection、record group 等 | 一个实例支持多个 Repository，每个 Repository 下有多个 Resource | 没有历史选择时自动恢复 cookie 中的 Repository，否则取第一个可访问 Repository；但只要有可访问 Repository 就仍显示 `Select Repository`，包括只有一个时 | Repository 是权限和数据分区；Resource、Accession、Archival Object 等按 Repository 归属，Resource 下挂 Archival Object 层级 |
| 国内多全宗产品与采购样本 | 全宗通常是显式管理对象或目录树维度，关联全宗号、立档单位、档案门类、档号规则和权限 | 集团、档案馆场景明确要求多全宗或多立档单位 | 公开材料能证明多全宗列表、按全宗分类、筛选或目录树切换；不能证明只有一个全宗时是否自动选中或隐藏控件 | 常见关系为集团/组织层级下多个立档单位或全宗，全宗下再配置门类和档案 |
| 国内单一机关采购样本 | 全宗号仍存在于档案数据和存储规则中，但产品可以不提供全宗管理模块 | 样本只面向一个检察院的单机使用 | 采购公告明确要求“无全宗管理”并“仅包含单机登录使用”；这能证明产品弱化了全宗管理，不能证明其是否存在自动选择逻辑 | 单一机关作为固定业务边界，全宗不作为日常可切换对象 |

## 国际开源系统

### AtoM（Access to Memory）

#### 1. 全宗不是独立业务实体，而是著录层级

AtoM 的主要用户实体包括 accession、archival description、authority record 和 archival institution 等，并没有独立 `Fonds` 实体。其浏览文档把 fonds、collection、series 视为档案著录层级；顶层描述下可以继续包含 subfonds、series、file 和 item。因此，AtoM 中的全宗更接近“档案著录树的一个可能根层级”，而不是系统级工作空间。

来源：

- [AtoM 2.10：Entity types](https://www.accesstomemory.org/en/docs/2.10/user-manual/overview/entity-types/)（访问日期：2026-07-30）
- [AtoM 2.10：Browse archival descriptions](https://www.accesstomemory.org/en/docs/2.10/user-manual/access-content/browse/)（访问日期：2026-07-30）

#### 2. 一个实例可服务多个档案机构，每个机构有多个顶层资源

AtoM 支持 multi-repository 安装。档案机构页面展示与该 repository 关联的 holdings，即多个顶层 archival description。官方文档建议只在顶层描述上链接 repository，下级描述自动继承，避免每一级重复保存或展示机构关系。

来源：

- [AtoM 2.10：Browse the holdings of an institution](https://www.accesstomemory.org/en/docs/2.10/user-manual/access-content/browse/#browse-the-holdings-of-an-institution)（访问日期：2026-07-30）

可概括为：

```text
AtoM 实例
└─ Archival institution / Repository
   ├─ 顶层 Archival description（level = fonds）
   │  └─ subfonds / series / file / item
   └─ 顶层 Archival description（level = collection 或其他）
```

#### 3. 单机构时隐藏重复展示，但依赖管理员配置

AtoM 2.10 提供 `Multiple repositories` 全局设置：

- 设为 `Yes` 时，在档案著录浏览页提供 repository facet，并支持按机构限定搜索；
- 设为 `No` 时，隐藏 repository facet、高级搜索中的 repository 条件、全局搜索中的机构限定，以及结果列表中重复的 repository 名称；
- “Browse archival institutions” 菜单不会随该设置自动消失，管理员可另行移除。

因此，AtoM 证明了“数据关系保留、单一场景减少重复信息”是成熟产品采用的模式。不过它是**显式配置的单/多资源库展示模式**，公开文档没有说明系统会按当前数量自动切换，也没有证明创建档案时会自动绑定唯一 repository。

来源：

- [AtoM 2.10：Settings — Multiple repositories](https://www.accesstomemory.org/en/docs/2.10/user-manual/administer/settings/#multiple-repositories)（访问日期：2026-07-30）

#### 4. 多机构时采用作用域浏览，而非每条记录反复询问

AtoM 的 institutional scoping 会在用户进入某一机构的 holdings 后显示机构标识、专用搜索框和浏览菜单，使后续浏览保持在该机构作用域内；全局搜索仍可以跨 repository。这个模式把“选择机构”提升为导航上下文，而不是每次操作都填写一次字段。

来源：

- [AtoM 2.10：Enable institutional scoping](https://www.accesstomemory.org/en/docs/2.10/user-manual/administer/settings/#enable-institutional-scoping)（访问日期：2026-07-30）

### ArchivesSpace

#### 1. Repository 是数据与权限分区，Resource 是著录树根

ArchivesSpace 把 Repository 定义为系统的主要分区机制；一个用户可以访问多个 Repository，而不少记录只属于一个 Repository。数据库文档显示 `resource`、`accession`、`archival_object` 等记录都有 `repo_id`。`Resource` 是档案描述树的根，`Archival Object` 必须归属于 Resource；Resource 的 level 可用于表达 fonds、collection、record group 等层级。

这意味着 ArchivesSpace 中：

- Repository 更像机构级或业务分区上下文；
- Resource 更像一个全宗、档案汇集或其他顶层档案资源；
- 全宗不是唯一允许的顶层资源类型。

来源：

- [ArchivesSpace：Permissions model](https://archivesspace.github.io/tech-docs/architecture/backend/#the-archivesspace-permissions-model)（访问日期：2026-07-30）
- [ArchivesSpace：Database — repository-scoped records and parent-child relationships](https://archivesspace.github.io/tech-docs/architecture/backend/database.html#repository-scoped-records)（访问日期：2026-07-30）
- [ArchivesSpace：API — Resources for a Repository](https://archivesspace.github.io/archivesspace/api/#get-a-list-of-resources-for-a-repository)（访问日期：2026-07-30）

可概括为：

```text
ArchivesSpace 实例
└─ Repository
   ├─ Resource（level = fonds / collection / recordgrp / ...）
   │  └─ Archival Object 层级
   └─ Resource
      └─ Archival Object 层级
```

#### 2. 自动恢复或选取 Repository，但选择器始终保留

ArchivesSpace 官方源码体现了两项明确行为：

1. 加载用户可访问的 Repository 后，如果会话没有当前 Repository，先尝试恢复 cookie；没有可用 cookie 时，取可访问列表的第一项并写入会话。
2. Staff UI 只判断可访问 Repository 列表是否非空；只要非空就渲染 `Select Repository` 下拉，没有“数量等于 1 时隐藏”的分支。

所以 ArchivesSpace 的单 Repository 体验是：**系统替用户建立默认上下文，但仍显式显示切换入口和当前 Repository**。创建和浏览 Resource 随当前 Repository 作用域进行，不需要每次在表单里重新选择 Repository。

来源：

- [ArchivesSpace 源码：`load_repository_list`](https://github.com/archivesspace/archivesspace/blob/183388560165779e86341a8c72394903518df0e2/frontend/app/controllers/application_controller.rb#L434-L458)（访问日期：2026-07-30）
- [ArchivesSpace 源码：Staff UI Repository selector](https://github.com/archivesspace/archivesspace/blob/183388560165779e86341a8c72394903518df0e2/frontend/app/views/shared/_header_user.html.erb#L3-L20)（访问日期：2026-07-30）

## 国内标准与公开系统样本

### 1. 机关档案的常态就是一个机关一个全宗

《机关档案管理规定》第二十四条明确：“机关全部档案构成一个全宗”；机关隶属关系、名称变化但工作性质和主要业务范围未变化时，维持原全宗不变。这直接支持“多数普通机关用户只有一个全宗”的判断。

来源：

- [国家档案局：《机关档案管理规定》](https://www.saac.gov.cn/daj/xzfgk/202112/6e4f1d909e2443fc85111b8f82973e37.shtml)（访问日期：2026-07-30）

### 2. 全宗仍是核心管理单元，而且并非永远等于当前组织

国家档案局发布的 GB/T 13967—2026《全宗管理规则》说明，全宗是收集、整理、保管和利用中的核心分类依据；新标准区分机构全宗与主题全宗，并规范联合全宗、汇集全宗以及机构改革、资产和产权变动时的处置。该标准已发布，计划自 2026 年 8 月 1 日实施。

这说明“常见单全宗”不能推导出“模型中不需要全宗”：档案馆、集团、合并重组单位和历史沿革场景仍需要多个全宗及其稳定身份。

来源：

- [国家档案局：国家标准《全宗管理规则》发布](https://www.saac.gov.cn/daj/szda/202604/870c50b55f5349049f8907a507ce59b7.shtml)（访问日期：2026-07-30）

### 3. 国内多全宗产品通常把全宗做成显式配置对象

重庆壹博的产品资料展示了多层级全宗列表：集团下配置子公司、分院或分公司等全宗单位，同时维护全宗号、内部编号、启用状态，并让全宗关联档案门类、工程项目模板、档号规则和借阅策略。其页面截图和说明能证明产品具备显式多全宗配置；但该页面是厂商自述，且未公开单全宗操作手册，不能据此判断只有一个全宗时是否隐藏选择器或自动绑定。

来源：

- [重庆壹博：档案系统—全宗管理](https://www.cqaoba.cn/files/filesgn/quanzong/)（访问日期：2026-07-30）

四川省公共资源交易信息网公示的采购结果也要求“支持集团架构，多全宗管理”。另一个档案馆采购文件要求管理库可以按全宗、门类、年度、保管期限等分类方式切换，并提供逐全宗的全宗卷管理。这些材料共同表明，国内集团和档案馆系统通常把全宗作为明确的管理、分类或导航维度。

来源：

- [四川省公共资源交易信息网：网络版电子管理软件采购结果与技术参数](https://ggzyjy.sc.gov.cn/jyxx/002002/002002003/20231016/8a69da538b1507a0018b17ea6ec160e4.html)（访问日期：2026-07-30）
- [陕西政府采购网：档案馆智能化建设项目采购文件，档案整理与全宗卷管理](https://www.ccgp-shaanxi.gov.cn/gpx-bid-file/ZF_JGBM_000003/zone/2025/1/12/project/gpx-template/8a69c50f97c135510197e8a86af128f4.pdf?accessCode=915dbe5df9a954409aeff52d78621420)（访问日期：2026-07-30）

### 4. 国内单一机关系统可以直接省略全宗管理模块

鄂托克旗人民检察院 2023 年档案数字化改造采购公告要求综合管理系统包含收集、管理、保存、利用、业务配置、系统管理和统计，但明确写明“无全宗管理和工作流管理模块”，并且“仅包含单机登录使用”。同一文件仍要求同一全宗采用一致的存储格式，说明它不是否认全宗，而是把全宗作为固定业务事实，不提供日常维护和切换能力。

这个案例可以证明单全宗产品会弱化甚至省略全宗管理模块；它没有公开界面或源码，因此不能进一步声称系统实现了“唯一全宗自动选择”。

来源：

- [鄂托克旗人民检察院：关于公开询价档案数字化改造项目的采购公告](https://www.nmetuoke.jcy.gov.cn/xwzx/gsgg/202311/t20231108_6012356.shtml)（访问日期：2026-07-30）

## 对本项目的具体借鉴

### 1. 保留独立全宗实体，但不要把它等同机构或租户

AtoM 和 ArchivesSpace 都证明，一个档案机构或 Repository 可以持有多个顶层档案资源；中国全宗规则又要求全宗在机构改革和历史变化中保持来源与延续性。因此本项目继续采用：

```text
机构 / 租户
└─ 全宗
   └─ 门类、案卷、档案及其下级结构
```

比“机构就是全宗”更稳妥。机构解决当前管理归属，全宗解决档案来源和历史连续性，两者生命周期不相同。

### 2. 把唯一全宗变成可确定上下文，不变成隐式数据

可借鉴 ArchivesSpace 的“自动建立上下文”和 AtoM 的“减少重复展示”：

- 当前用户恰好只有一个启用且有权访问的全宗时，进入业务页面后自动使用它；
- 创建、导入、组卷等前端不再要求用户重复选择；
- 页面标题、面包屑或低干扰标签仍显示当前全宗；
- 请求中仍携带明确全宗标识，服务端校验存在性、启用状态和权限；
- 不把“缺少全宗参数”解释为“随便取第一个”，自动确定只发生在受控的前端或应用上下文建立阶段。

### 3. 多全宗时采用持久上下文，而非每张表单重新选择

当用户可访问多个全宗时，可以像 ArchivesSpace Repository 和 AtoM institutional scoping 一样：

- 在全局业务区显示当前全宗；
- 提供明确切换入口；
- 浏览、创建和搜索默认继承当前全宗；
- 需要跨全宗检索或统计时，提供独立的“全部全宗”作用域或筛选，而不是让普通业务操作默认跨宗；
- 切换全宗后同步更新可用门类、档号规则、权限和统计口径。

### 4. 由数据事实驱动界面，不额外引入“单全宗模式”业务开关

AtoM 使用管理员开关解决兼容性和门户部署问题，但这也带来配置与真实数据不一致的可能。对本项目而言，若需求仅是服务“当前用户只有一个可用全宗”的高频场景，更小的实现是直接根据可访问全宗数量呈现：

- `0` 个：阻止进入需全宗的业务，并引导初始化或申请权限；
- `1` 个：自动进入，隐藏无意义的选择控件，保留只读上下文提示；
- `N` 个：显示切换器和筛选。

这是一项产品建议，不是外部系统已经形成的统一规范。

### 5. 全宗管理保持低频但完整

普通单全宗单位很少进入全宗管理，但以下能力仍应保留给管理员：

- 全宗创建、停用和基本信息维护；
- 全宗号唯一性与档号规则；
- 可用门类、权限和责任单位关联；
- 历史沿革、机构变动和移交处置记录；
- 全宗卷及跨全宗统计。

因此应降低全宗管理在日常导航中的权重，而不是降低它在领域模型和服务端约束中的重要性。

## 尚不能从公开资料确认的事项

- 未找到 AtoM 会根据 repository 实际数量自动启用或关闭 `Multiple repositories` 的证据；官方文档把它描述为管理员设置。
- ArchivesSpace 源码能确认 Repository 的自动恢复/首项选择和选择器渲染条件，但没有“只有一个时隐藏”的逻辑。
- 国内公开采购材料通常描述功能清单、分类方式和验收要求，很少公开完整交互流程；不能据此判断单全宗时是否自动选中、是否隐藏表单字段。
- 重庆壹博页面能证明其宣称的产品模型，不能替代真实部署验收或源码审查。

## 最终判断

“多数单位只有一个全宗”与“系统必须支持多个全宗”并不冲突。其他系统的共同做法是保留多资源能力，同时通过作用域、继承、默认上下文或隐藏重复展示来降低单一场景的操作成本。

本项目宜采用：

> 领域上显式支持多全宗；交互上以唯一可访问全宗作为自动确定的业务上下文；只有存在真实选择时才显示选择器，但始终让数据归属可见且由服务端校验。
