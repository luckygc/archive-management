## MODIFIED Requirements

### Requirement: 归档接收系统入口

系统 SHALL 提供归档接收模块入口，用于承载档案信息包接收和归档前处理能力。

#### Scenario: 查询归档接收入口概览

- **WHEN** 前端请求 `GET /api/v1/intake`
- **THEN** 系统 SHALL 返回归档接收入口概览
- **AND** 响应 SHALL 明确表示本地档案信息包接收可用
- **AND** 响应 SHALL 明确表示当前未配置 NAS、SFTP、HTTP 或其他外部连接

### Requirement: 归档接收与档案核心边界

归档接收 SHALL 负责电子档案移交信息包、四性检测、交接确认和入库编排，档案核心 SHALL 负责确认接收后形成的正式档案资产。系统 SHALL 以 repository role 作为档案和案卷记录阶段的唯一真相源，其中 `INTAKE` 表示预归档、`HOLDING` 表示正式档案；电子档案移交信息包与业务部门实物移交接收均 SHALL 使用独立业务记录表达。

#### Scenario: 档案信息包尚未处理成功

- **WHEN** 档案信息包处于接收、解析或校验阶段
- **THEN** 系统 SHALL 将其记录为独立的归档接收包
- **AND** 系统 SHALL NOT 将接收包本身视为档案条目
- **AND** 系统 SHALL NOT 在自动检测或人工复核完成前生成正式档案条目

#### Scenario: 信息包确认接收

- **WHEN** 信息包自动检测通过且接收人员完成人工复核和交接确认
- **THEN** 系统 SHALL 直接在系统 `HOLDING` 业务库中原子生成正式档案条目
- **AND** 系统 SHALL NOT 先在 `INTAKE` 业务库生成预归档条目
- **AND** 系统 SHALL NOT 为生成条目写入 `DRAFT`、`ARCHIVED` 或其他平行电子状态
- **AND** 接收包 SHALL 关联所有生成条目
- **AND** 任一条目或电子文件入库失败 SHALL NOT 留下部分正式档案

#### Scenario: 通过系统库角色识别档案生命周期

- **WHEN** 系统展示、查询或变更档案或案卷
- **THEN** 系统 SHALL 以条目所在 repository role 判断其生命周期阶段
- **AND** `INTAKE` 中的条目 SHALL 表示预归档档案
- **AND** `HOLDING` 中的条目 SHALL 表示正式档案
- **AND** 档案及案卷的 API 和前端 SHALL NOT 暴露或接受 `electronicStatus`

#### Scenario: 数据已经正式成为档案记录

- **WHEN** 移交信息包状态为 `ACCEPTED`
- **THEN** 生成条目的后续管理能力 SHALL 归入档案核心模块

## ADDED Requirements

### Requirement: DA/T 93 离线档案信息包合同

系统 SHALL 以 DA/T 93—2022 附录 B 的“件”级移交信息包结构作为离线 ZIP 接收格式基线，并 SHALL 将 ZIP 仅视为标准目录树的网页传输封装。

#### Scenario: 上传合法信息包

- **WHEN** 用户上传的 ZIP 根内容或唯一顶层目录包含 `说明文件.TXT`、`目录文件.XML`、至少一个全宗目录和可选 `其他` 目录
- **AND** `目录文件.XML` 包含 1 至 100 个以“件”管理的档案目录项
- **AND** 每个目录项的档号可唯一关联到全宗/分类目录中的同名电子档案目录
- **THEN** 系统 SHALL 长期保存原始信息包及其文件名、大小和 SHA-256 摘要
- **AND** 系统 SHALL 记录格式配置 `DAT93_ITEM`
- **AND** 每个条目 SHALL 使用目录路径中的全宗编码、第一层分类编码和目录 XML 中的档号及著录信息表达

#### Scenario: 校验目录、元数据与内容关联

- **WHEN** 档号不存在同名档案目录、关联到多个目录或存在未被目录 XML 著录的档案目录
- **OR** 档案目录缺少元数据 XML 或内容数据
- **OR** 说明文件、目录 XML、档案元数据 XML 不可安全解析
- **THEN** 系统 SHALL 将接收记录标记为 `FAILED`
- **AND** 系统 SHALL NOT 生成正式档案条目或电子文件关系

#### Scenario: 信息包确认接收并挂接电子文件

- **WHEN** 说明文件、目录数据、档案元数据和全部内容数据通过自动检测且接收人员确认接收
- **THEN** 系统 SHALL 将档案元数据 XML 和内容数据保存到对象存储
- **AND** 系统 SHALL 按所属档案目录、原文件名、用途和稳定顺序挂接电子文件
- **AND** 生成条目及其电子文件 SHALL 位于同一处理事务的结果边界内

#### Scenario: 信息包违反安全边界

- **WHEN** ZIP 条目使用绝对路径、反斜杠、父目录跳转或重复路径
- **OR** ZIP 条目被加密、使用不支持的压缩算法、包含重复路径或标准目录树之外的未归属文件
- **OR** 上传大小超过 50 MiB、说明或目录文件超过 2 MiB、条目数超过 100、内容文件数超过 500、单文件超过 50 MiB、解压总量超过 200 MiB 或 ZIP 条目超过 1000
- **THEN** 系统 SHALL 拒绝处理
- **AND** 系统 SHALL 只把 ZIP 内容流式写入随机临时文件，SHALL NOT 按 ZIP 路径在文件系统展开

### Requirement: 移交信息包四性检测报告

系统 SHALL 依据 DA/T 70—2018 的移交与接收环节保存真实性、完整性、可用性和安全性检测结果，并区分自动通过、警告和需要人工或外部能力复核。

#### Scenario: 自动检测标准结构和数据关联

- **WHEN** 系统解析信息包
- **THEN** 系统 SHALL 自动检测说明文件和目录文件规范性、目录结构、元数据可读性、必填项、档号唯一性、元数据与内容关联、总件数、总字节数、内容数量、压缩算法和加密状态
- **AND** 阻断项失败 SHALL 使包处理失败
- **AND** 结果 SHALL 使用可追踪的检测编码、四性类别、结论和说明保存

#### Scenario: 检测依赖包外证据或外部能力

- **WHEN** 检测项需要移交前摘要、电子签名/印章/时间戳可信验证、人工打开浏览、杀毒软件或离线载体实物
- **THEN** 系统 SHALL 将该项标记为 `MANUAL_REVIEW`
- **AND** 系统 SHALL NOT 将该项伪造为自动通过
- **AND** 自动处理完成 SHALL 只进入待人工复核状态，SHALL NOT 表示正式移交接收手续或正式入库完成

### Requirement: 包级接收状态

系统 SHALL 以接收包为单位保存 `RECEIVED`、`CHECKING`、`PENDING_REVIEW`、`ACCEPTING`、`ACCEPTED`、`REJECTED` 或 `FAILED` 状态，并记录接收人、复核人和各阶段时间。

#### Scenario: 自动检测通过

- **WHEN** 用户上传的信息包通过全部阻断性自动检测
- **THEN** 接收记录 SHALL 为 `PENDING_REVIEW`
- **AND** 响应 SHALL 包含自动检测完成时间和四性检测摘要

#### Scenario: 信息包处理失败

- **WHEN** ZIP、清单或任一档案条目校验失败
- **THEN** 接收记录 SHALL 为 `FAILED`
- **AND** 接收记录 SHALL 保存可理解的失败原因
- **AND** 系统 SHALL NOT 生成正式档案条目
- **AND** 原始信息包 SHALL 继续保留用于核验

#### Scenario: 人工复核后确认接收

- **WHEN** 接收人员确认来源固化信息、内容可读性、病毒检测、离线载体安全和交接手续均合格
- **THEN** 接收记录 SHALL 从 `PENDING_REVIEW` 进入 `ACCEPTING` 并最终成为 `ACCEPTED`
- **AND** 正式档案条目和电子文件 SHALL 原子且直接进入系统 `HOLDING` 库
- **AND** 系统 SHALL NOT 为正式档案写入 `DRAFT` 或 `ARCHIVED`
- **AND** 接收记录 SHALL 保存复核人、复核时间和交接备注

#### Scenario: 人工复核后退回

- **WHEN** 接收人员认为信息包不符合接收要求并填写退回原因
- **THEN** 接收记录 SHALL 为 `REJECTED`
- **AND** 系统 SHALL NOT 生成正式档案条目
- **AND** 原包、检测报告和退回原因 SHALL 保留

### Requirement: 档案信息包 API

系统 SHALL 通过包资源 API 提供上传、当前用户历史和详情查询。

#### Scenario: 创建接收包资源

- **WHEN** 已认证且具有 `archive:item:create` 权限的用户向 `POST /api/v1/archive-intake-packages` 提交 multipart `file`
- **THEN** 系统 SHALL 返回 `201 Created`
- **AND** 响应 SHALL 直接返回自动检测后的 `PENDING_REVIEW` 或 `FAILED` 包详情

#### Scenario: 查询当前用户接收历史

- **WHEN** 已认证且具有 `archive:item:read` 权限的用户请求 `GET /api/v1/archive-intake-packages`
- **THEN** 系统 SHALL 使用 `limit` 和不透明 `cursor` 返回 `CursorPageResponse`
- **AND** 结果 SHALL 按 `createdAt DESC, id DESC` 稳定排序
- **AND** 结果 SHALL 只包含当前用户提交的接收记录

#### Scenario: 查询接收包详情

- **WHEN** 已认证且具有 `archive:item:read` 权限的用户请求 `GET /api/v1/archive-intake-packages/{id}`
- **THEN** 系统 SHALL 返回属于当前用户的包详情和生成条目
- **AND** 系统 SHALL 对不存在或不属于当前用户的记录返回资源不存在

#### Scenario: 下载原始接收包

- **WHEN** 已认证且具有 `archive:item:read` 权限的提交人请求 `POST /api/v1/archive-intake-packages/{id}:createDownloadLink`
- **THEN** 系统 SHALL 返回仅当前用户可用的短期下载地址和过期时间
- **AND** 不存在或不属于当前用户的记录 SHALL 返回资源不存在

#### Scenario: 确认接收

- **WHEN** 当前提交人具有 `archive:item:create` 权限并向 `POST /api/v1/archive-intake-packages/{id}:accept` 提交完整人工复核确认
- **THEN** 系统 SHALL 重新读取并解析长期保存的原包
- **AND** 成功时 SHALL 返回 `ACCEPTED` 包详情和生成的正式档案条目
- **AND** 非 `PENDING_REVIEW` 状态 SHALL 拒绝该动作

#### Scenario: 退回信息包

- **WHEN** 当前提交人具有 `archive:item:create` 权限并向 `POST /api/v1/archive-intake-packages/{id}:reject` 提交非空退回原因
- **THEN** 系统 SHALL 返回 `REJECTED` 包详情
- **AND** 非 `PENDING_REVIEW` 状态 SHALL 拒绝该动作

### Requirement: 归档接收工作台

系统 SHALL 在 PC 归档接收页面提供上传与接收历史工作台。

#### Scenario: 上传并查看成功结果

- **WHEN** 用户选择 ZIP 并提交
- **THEN** 页面 SHALL 在提交期间禁用重复上传
- **AND** 成功后 SHALL 刷新接收历史
- **AND** 页面 SHALL 展示包状态和生成条目摘要
- **AND** 页面 SHALL 展示内容文件数量、总量、自动检测通过/警告/待人工复核数量和每个条目的电子文件数量
- **AND** 页面 SHALL 明确 `PENDING_REVIEW` 尚未完成正式接收登记或入库
- **AND** 页面 SHALL 在逐项确认人工/外部检测和交接手续后才允许“确认接收”
- **AND** 页面 SHALL 提供填写原因的“退回”动作
- **AND** 用户 SHALL 能下载原始接收包

#### Scenario: 查看失败结果

- **WHEN** 接收包处理失败
- **THEN** 页面 SHALL 展示 `FAILED` 或 `REJECTED` 状态及对应原因
- **AND** 用户 SHALL 能选择修正后的新信息包重新上传

#### Scenario: 加载列表状态

- **WHEN** 接收历史正在加载、为空或请求失败
- **THEN** 页面 SHALL 分别展示可识别的加载、空状态或错误重试反馈
