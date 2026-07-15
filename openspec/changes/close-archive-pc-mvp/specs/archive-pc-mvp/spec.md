## ADDED Requirements

### Requirement: 档案列表游标分页闭环

档案搜索和档案管理页面 SHALL 使用项目统一游标合同浏览当前用户可访问的档案结果。

#### Scenario: 浏览超过一百条且排序值重复的档案

- **WHEN** 当前查询返回超过 100 条档案且用户排序字段存在重复值
- **THEN** 页面 SHALL 使用响应的 `next` 和 `prev` 不透明游标提供上一页和下一页操作
- **AND** 翻页请求 SHALL 保持首次查询的已提交筛选、搜索、排序、页大小和业务范围不变
- **AND** 服务端 SHALL 在用户排序后应用稳定唯一的兜底排序
- **AND** 连续浏览全部结果 SHALL 不重复也不遗漏档案
- **AND** 页面 SHALL 在不存在下一页、请求加载中或翻页动作不可用时展示对应禁用状态

#### Scenario: 提交新的查询条件

- **WHEN** 用户提交新的筛选或搜索条件，或者修改排序或页大小
- **THEN** 页面 SHALL 清除旧 `self`、`prev`、`next` 和当前游标
- **AND** 页面 SHALL 使用新的已提交查询发起不带旧游标的第一页请求
- **AND** 未提交的草稿条件 SHALL NOT 混入当前结果的翻页或刷新请求

#### Scenario: 刷新当前结果

- **WHEN** 用户刷新当前列表或完成一项会改变档案状态的操作
- **THEN** 页面 SHALL 复用当前已提交查询、排序和页大小
- **AND** 页面 SHALL NOT 用搜索表单中尚未提交的草稿替换当前查询

#### Scenario: 游标已经失效

- **WHEN** 服务端因数据变化或请求与游标绑定状态不一致而返回游标错误 ProblemDetail
- **THEN** 页面 SHALL 提示结果上下文已经变化
- **AND** 页面 SHALL 清除旧游标并允许使用相同已提交查询从第一页重试

### Requirement: 档案生命周期操作

档案管理页面 SHALL 为具备相应权限的用户提供查看、编辑、锁定、解锁、删除、电子文件和审计入口，并由服务端守护状态转换。

#### Scenario: 锁定未锁定档案

- **WHEN** 具备锁定权限的用户对未锁定档案发起锁定
- **THEN** 页面 SHALL 要求用户填写锁定原因后调用档案 `:lock` 动作
- **AND** 服务端 SHALL 校验权限、数据范围和当前状态
- **AND** 操作成功后页面 SHALL 刷新当前已提交查询而不重置筛选条件

#### Scenario: 解锁已锁定档案

- **WHEN** 具备锁定权限的用户对已锁定档案发起解锁
- **THEN** 页面 SHALL 要求用户二次确认后调用档案 `:unlock` 动作
- **AND** 服务端 SHALL 校验权限、数据范围和当前状态
- **AND** 操作成功后页面 SHALL 刷新当前已提交查询

#### Scenario: 删除可删除档案

- **WHEN** 具备删除权限的用户删除一条当前允许删除的档案
- **THEN** 页面 SHALL 要求用户二次确认后调用档案资源 `DELETE`
- **AND** 服务端 SHALL 校验权限、数据范围、锁定状态和其他删除前置条件
- **AND** 操作成功后页面 SHALL 刷新当前已提交查询

#### Scenario: 服务端拒绝生命周期操作

- **WHEN** 档案状态、权限、数据范围或并发条件不允许当前操作
- **THEN** 服务端 SHALL 返回项目统一 ProblemDetail 和稳定错误码
- **AND** 页面 SHALL 保留当前查询上下文并展示可执行原因
- **AND** 页面 SHALL NOT 通过解析异常类名或自由文本推断业务状态

### Requirement: 档案完整字段编辑

档案详情和编辑器 SHALL 使用同一服务端详情合同展示和维护固定参考数据、实物字段与动态业务字段。

#### Scenario: 编辑档案完整字段

- **WHEN** 用户打开可编辑档案
- **THEN** 页面 SHALL 回填密级、保管期限、实物字段值和动态业务字段值
- **AND** 密级与保管期限 SHALL 从现有启用参考数据中选择
- **AND** 页面 SHALL 按现有元数据字段类型和校验规则渲染实物字段与动态字段
- **AND** 保存请求 SHALL 提交用户实际编辑后的 `securityLevelId`、`retentionPeriodId`、`physicalFields` 和 `dynamicFields`
- **AND** 页面 SHALL NOT 固定提交空的 `physicalFields`

#### Scenario: 只读查看档案完整字段

- **WHEN** 用户以详情模式查看档案
- **THEN** 页面 SHALL 展示与编辑模式相同的固定参考数据、实物字段和动态字段合同
- **AND** 所有字段控件和明细行操作 SHALL 处于只读或禁用状态

#### Scenario: 字段保存校验失败

- **WHEN** 服务端返回带 `fieldViolations` 的字段校验错误
- **THEN** 页面 SHALL 保留用户输入
- **AND** 页面 SHALL 将字段错误回填到对应表单控件

### Requirement: 案卷管理闭环

系统 SHALL 提供案卷 PC 页面，使用户可以在权限和数据范围内筛选、创建、查看案卷并维护卷内档案。

#### Scenario: 分页筛选并查看案卷

- **WHEN** 用户按全宗和档案分类提交案卷筛选
- **THEN** `GET /api/v1/archive-volumes` SHALL 返回 `CursorPageResponse<ArchiveVolumeResponse>`
- **AND** `limit` 和 `cursor` SHALL 通过 URL query 参数提交
- **AND** 全宗、分类筛选和有效排序 SHALL 进入 cursor 查询摘要
- **AND** 案卷列表 SHALL 默认按 `createdAt DESC, id DESC` 稳定排序
- **AND** 带 cursor 的请求 SHALL 重复提交与首次请求相同的全宗、分类筛选和排序
- **AND** 页面 SHALL 使用现有 cursor 组件展示当前用户数据范围内匹配的案卷
- **AND** 用户 SHALL 能打开案卷详情并查看其中的档案
- **AND** 卷内档案查询 SHALL 复用档案列表合同并按 `volumeId` 限定

#### Scenario: 创建案卷

- **WHEN** 具备档案创建权限的用户提交有效案卷信息
- **THEN** 服务端 SHALL 创建独立案卷资源
- **AND** 创建成功后页面 SHALL 刷新当前已提交筛选
- **AND** 系统 SHALL NOT 将案卷作为档案条目保存

#### Scenario: 将档案加入案卷

- **WHEN** 用户为案卷选择一条允许归入的档案并确认
- **THEN** 页面 SHALL 使用 `{ itemId, displayOrder? }` 请求体调用案卷 `:addItem` 动作
- **AND** 服务端 SHALL 校验用户权限、案卷与档案数据范围、分类兼容性和当前状态
- **AND** 操作成功 SHALL 返回 `204 No Content`
- **AND** Web 客户端 SHALL 返回 `Promise<void>` 且 SHALL NOT 解析 `ArchiveVolumeResponse`
- **AND** 成功后页面 SHALL 刷新当前案卷内档案

### Requirement: 档案关系维护

档案详情 SHALL 支持查看、创建和删除结构化的档案条目关联。

#### Scenario: 查看档案关系

- **WHEN** 具备档案读取权限的用户打开档案关系页签
- **THEN** `GET /api/v1/archive-items/{archiveItem}/relations` SHALL 返回 `CursorPageResponse<ArchiveItemRelationResponse>`
- **AND** `limit` 和 `cursor` SHALL 通过 URL query 参数提交
- **AND** 关系列表 SHALL 固定按 `id ASC` 稳定排序
- **AND** 如果请求保留 `depth` 参数，`depth` SHALL 进入 cursor 查询摘要
- **AND** 带 cursor 的请求 SHALL 重复提交与首次请求相同的 `depth`
- **AND** 页面 SHALL 使用现有 cursor 组件展示当前档案可见的来源、目标和关联档案摘要
- **AND** 服务端 SHALL 过滤当前用户无权读取的关联档案

#### Scenario: 从搜索结果创建关系

- **WHEN** 具备档案更新权限的用户创建档案关系
- **THEN** 页面 SHALL 允许用户通过分类、关键字和游标搜索选择目标档案
- **AND** 页面 SHALL NOT 要求用户手工输入数据库 ID
- **AND** 当前档案自身 SHALL NOT 成为可选目标
- **AND** 服务端 SHALL 拒绝自关联、重复关联或越权关联
- **AND** 成功后页面 SHALL 只刷新关系页签

#### Scenario: 删除档案关系

- **WHEN** 具备档案更新权限的用户确认删除一条已有关系
- **THEN** 服务端 SHALL 删除指定关系资源
- **AND** 成功后页面 SHALL 只刷新关系页签

### Requirement: 分类明细表定义

分类配置页面 SHALL 接通现有明细表定义、字段定义和动态建表能力。

#### Scenario: 创建明细表及字段

- **WHEN** 具备 `archive:metadata:manage` 权限的用户为分类创建明细表和字段
- **THEN** 页面 SHALL 允许维护明细表编码、名称、排序及现有元数据支持的字段属性
- **AND** 字段 SHALL 只使用现有字段类型、编码、名称、列名、精确检索和排序合同
- **AND** 系统 SHALL NOT 提供通用低代码或拖拽表格设计器

#### Scenario: 构建明细动态表

- **WHEN** 用户对包含有效字段定义的明细表执行构建
- **THEN** 页面 SHALL 调用明细表 `:build` 动作
- **AND** 服务端 SHALL 按 `archive-metadata` 规格创建或更新动态明细表
- **AND** 构建失败 SHALL 保留明细表和字段定义并提供原位重试

#### Scenario: 无权维护明细定义

- **WHEN** 用户不具备 `archive:metadata:manage` 权限
- **THEN** 分类页面 SHALL NOT 展示明细表维护入口
- **AND** 服务端 SHALL 拒绝其直接调用明细表写入或构建 API

### Requirement: 档案明细行数据

系统 SHALL 将已构建明细表的行数据作为档案条目下的子资源进行查询、创建、修改和逻辑删除。

#### Scenario: 读取条目范围明细定义

- **WHEN** 具备档案读取权限的用户打开数据范围内的档案详情或编辑器
- **THEN** 服务端 SHALL 通过 `GET /api/v1/archive-items/{archiveItem}/line-tables` 返回该条目分类下已启用且已构建的明细表和字段定义
- **AND** 响应 SHALL 只包含界面需要的表与字段标识、名称、类型和排序
- **AND** 响应 SHALL NOT 包含物理表名、物理列名或元数据管理属性
- **AND** 服务端 SHALL NOT 要求用户具备 `archive:metadata:manage` 权限
- **AND** 服务端 SHALL 校验 `archive:item:read` 权限和条目数据范围

#### Scenario: 分页读取明细行

- **WHEN** 用户读取档案指定明细表的行数据
- **THEN** 服务端 SHALL 校验档案读取权限、数据范围、分类与明细表归属关系
- **AND** 响应 SHALL 使用统一 `CursorPageResponse<ArchiveItemLineRowResponse>`
- **AND** `limit` 和 `cursor` SHALL 通过 URL query 参数提交
- **AND** 排序 SHALL 固定为 `lineOrder ASC, id ASC`

#### Scenario: 新增明细行

- **WHEN** 具备档案更新权限的用户为未锁定档案提交 `CreateArchiveItemLineRowRequest`
- **THEN** 服务端 SHALL 校验明细表已构建且属于档案分类
- **AND** 服务端 SHALL 按字段定义校验并映射 `values`
- **AND** 服务端 SHALL 创建行并返回 `ArchiveItemLineRowResponse`
- **AND** 动态表名和列名 SHALL 只来自服务端已验证的元数据白名单
- **AND** 请求值 SHALL 使用 SQL 参数绑定

#### Scenario: 修改明细行

- **WHEN** 具备档案更新权限的用户提交 `PatchArchiveItemLineRowRequest` 修改未锁定档案中的已有明细行
- **THEN** 服务端 SHALL 校验行属于路径中的档案和明细表
- **AND** `values` 中缺失的字段键 SHALL 保持原值不变
- **AND** `values` 中显式为 `null` 的字段键 SHALL 清空对应动态字段
- **AND** 缺失的 `lineOrder` SHALL 保持原行顺序不变
- **AND** 显式为 `null` 的 `lineOrder` SHALL 返回 `INVALID_ARGUMENT` ProblemDetail
- **AND** 服务端 SHALL 只更新请求中出现且存在于字段定义中的值与行顺序
- **AND** 页面 SHALL 在保存成功后刷新对应明细行列表

#### Scenario: 删除明细行

- **WHEN** 具备档案更新权限的用户确认删除已有明细行
- **THEN** 服务端 SHALL 校验行属于路径中的档案和明细表
- **AND** 服务端 SHALL 写入逻辑删除标记和删除审计字段
- **AND** 后续普通明细行查询 SHALL 排除该行

#### Scenario: 详情或锁定状态查看明细

- **WHEN** 用户以详情模式查看档案或档案已锁定
- **THEN** 页面 SHALL 允许具备读取权限的用户查看明细行
- **AND** 页面 SHALL 禁用新增、编辑和删除明细行操作
- **AND** 服务端 SHALL 拒绝绕过页面直接提交的写请求

### Requirement: 统一功能权限体验

PC 菜单、分组、路由、页签和按钮 SHALL 使用同一权限判断，并由服务端继续执行最终授权。

#### Scenario: 超级管理员访问功能

- **WHEN** 当前用户的服务端权限摘要标记 `superAdmin=true`
- **THEN** 前端权限判断 SHALL 将其视为拥有全部现在和未来权限码
- **AND** 页面 SHALL NOT 要求服务端枚举每个权限码后才显示功能

#### Scenario: 普通用户查看菜单

- **WHEN** 普通用户登录并获得服务端返回的权限码集合
- **THEN** 菜单叶子、页签和按钮 SHALL 按该集合判断可见或可用状态
- **AND** 不包含任何可见子菜单的分组 SHALL NOT 显示
- **AND** 前端 SHALL NOT 维护独立于服务端摘要的业务权限集合

#### Scenario: 直接访问无权路由

- **WHEN** 用户通过地址直接访问缺少所需权限的路由
- **THEN** 导航守卫 SHALL 将用户带到统一无权限页面
- **AND** 页面 SHALL NOT 依赖业务 API 请求失败来表达导航权限
- **AND** 服务端 SHALL 仍拒绝用户对受保护资源的直接请求

#### Scenario: 会话期间撤销当前页面权限

- **WHEN** 已初始化的权限摘要成功刷新
- **THEN** 权限 Store SHALL 以一个完整版本原子替换初始化状态、超级管理员标记和权限码集合
- **AND** Store 对外暴露的快照及权限码集合 SHALL 为深度只读且不可变，调用方不得绕过服务端响应直接改写
- **AND** 较早发起的并发响应或重置前的在途响应 SHALL NOT 覆盖较新版本
- **WHEN** 最新成功版本不再包含当前页面或已打开页签所需权限
- **THEN** PC SHALL 立即隐藏当前无权缓存内容并移除其他无权页签
- **AND** 仅在最新 403 导航成功后移除当前无权页签
- **AND** 403 导航被取消或拒绝时 SHALL 保留当前页签并显示稳定的内联无权限状态
- **AND** 过期的 403 导航 SHALL NOT 删除已恢复权限的当前页签
- **AND** 用户随后离开该无权路由时 SHALL 清理被保留的无权页签及缓存
- **WHEN** 尚在五分钟有效期内的已初始化权限摘要刷新失败
- **THEN** PC SHALL 保留上一完整成功版本、当前页面和页签
- **AND** 注销或权限摘要重置为未初始化状态 SHALL NOT 触发额外无权限导航

#### Scenario: 有界刷新会话权限

- **WHEN** 已登录 AppShell 挂载
- **THEN** PC SHALL 每 60 秒以及窗口重新获得焦点或页面重新可见时检查权限摘要
- **AND** 仅在摘要未就绪或距离五分钟有效期结束不超过 60 秒时发起自动刷新
- **AND** PC SHALL 为当前有效快照按 `validUntil` 设置单个可取消到期计时器，不得因 60 秒检查周期的错相而延迟失败关闭
- **AND** 刷新成功改变 `validUntil` 时 SHALL 取消旧到期计时器并按新到期点重排，重置或卸载时 SHALL 清除该计时器
- **AND** 定时、焦点和可见性触发 SHALL 复用同一在途请求，并对快速失败的自动重试按 60 秒节流
- **AND** AppShell 卸载后 SHALL 清除定时器和事件监听
- **WHEN** 五分钟有效期届满且刷新仍失败
- **THEN** 权限判断和受保护路由 SHALL 在 `validUntil` 到点立即失败关闭，停止渲染受保护内容
- **AND** 页面从后台恢复可见时 SHALL 先同步提交已经到期的状态，即使自动刷新请求仍处于节流窗口
- **AND** 页面 SHALL 显示可原位重试的权限校验失败状态，不得把校验失败伪装为 403 无权限
- **AND** 导航守卫 SHALL 在判断受保护目标路由前确保权限摘要仍有效
- **WHEN** 用户手动重新校验且请求成功
- **THEN** PC SHALL 原子恢复最新权限视图并重新应用路由、菜单、页签和按钮判断

#### Scenario: 菜单分组显式声明空子路由

- **WHEN** 菜单路由声明 `children: []` 且没有自身页面组件
- **THEN** PC SHALL 将其视为没有可见叶子的分组并隐藏
- **AND** `menu:false` 的叶子路由或带自身组件的非菜单父路由 SHALL 仍可按权限直接访问

#### Scenario: 单能力用户访问授权管理

- **WHEN** 用户仅有 `authorization:permission:manage`
- **THEN** 授权管理 SHALL 只加载角色目录、权限目录和角色功能权限
- **AND** 页面 SHALL NOT 请求用户、部门或数据范围接口
- **WHEN** 用户仅有 `archive:data-scope:manage`
- **THEN** 授权管理 SHALL 只加载角色、最小用户选项、部门及数据范围目录和主体范围
- **AND** 页面 SHALL NOT 请求功能权限接口
- **AND** 页面 SHALL 通过 `GET /api/v1/authentication-user-options` 的 `CursorPageResponse`、`limit` 和 `cursor` 获取用户选项
- **AND** 用户选项 SHALL 只包含 `id`、`username` 和 `displayName`，不得包含邮箱、手机、部门、启用状态或创建时间
- **AND** 用户选项目录 SHALL 仅接受 `archive:data-scope:manage`
- **AND** `GET /api/v1/authentication-users` SHALL 继续仅接受 `authentication:user:manage`
- **AND** 角色/用户详情和写入 SHALL 继续要求各自精确管理权限

#### Scenario: 授权管理能力在页面存续期间变化

- **WHEN** 授权管理页面存续期间从单能力变为双能力
- **THEN** 页面 SHALL 在当前目录批次完成后只补载新获得能力所需的目录
- **AND** 页面 SHALL NOT 并发重复请求同一目录
- **WHEN** 页面存续期间撤销一项能力
- **THEN** 页面 SHALL 清除该能力专属的主体类型、选择、展示值、编辑值和弹窗
- **AND** 已撤销能力的旧目录或详情响应 SHALL NOT 回填页面
- **WHEN** 随后重新授予该能力
- **THEN** 页面 SHALL 发起新的必要目录请求并仅采用当前能力版本的响应

### Requirement: 真实工作台摘要

工作台 SHALL 展示当前用户数据范围内的真实档案摘要，并在审批能力完成前不展示虚构待办。

#### Scenario: 获取工作台摘要

- **WHEN** 已认证用户请求 `GET /api/v1/workspace-summary`
- **THEN** 服务端 SHALL 返回 `WorkspaceSummaryResponse`
- **AND** 响应 SHALL 包含可访问档案数、草稿数、锁定数和电子文件数
- **AND** 每项统计 SHALL 复用档案查询的数据范围和逻辑删除语义
- **AND** 空数据范围 SHALL 返回四项均为零的摘要

#### Scenario: 无档案读取权限的用户进入工作台

- **WHEN** 已认证用户没有 `archive:item:read` 权限并请求工作台摘要
- **THEN** 服务端 SHALL 返回四项均为零的 `WorkspaceSummaryResponse`
- **AND** 服务端 SHALL NOT 枚举档案分类或执行动态表统计

#### Scenario: 展示真实摘要

- **WHEN** 工作台摘要请求成功
- **THEN** 页面 SHALL 展示服务端返回的四项统计
- **AND** 页面 SHALL NOT 展示硬编码统计、演示待办或虚构近期事项

#### Scenario: 摘要查询失败

- **WHEN** 工作台摘要请求失败
- **THEN** 摘要区域 SHALL 显示独立的可重试错误状态
- **AND** 失败 SHALL NOT 阻断菜单、页签或其他工作区导航

### Requirement: 关键读取区域原位错误恢复

档案搜索、档案管理、案卷和工作台等关键读取区域 SHALL 在请求失败时保留用户上下文并提供原位重试。

#### Scenario: 列表加载失败后重试

- **WHEN** 档案搜索、档案管理或案卷列表加载失败
- **THEN** 页面 SHALL 在对应内容容器内展示错误原因和可聚焦的重试动作
- **AND** 页面 SHALL 保留草稿条件、已提交查询、排序、页大小和已有结果
- **AND** 重试 SHALL 重新执行失败时的同一已提交请求

#### Scenario: 写操作失败后恢复

- **WHEN** 保存、锁定、解锁、删除、关系或明细行写操作失败
- **THEN** 页面 SHALL 保留尚可继续编辑的用户输入和当前对象上下文
- **AND** 瞬时操作结果 SHALL 使用统一消息反馈
- **AND** 字段错误 SHALL 使用 ProblemDetail 的 `fieldViolations` 回填
- **AND** 错误展示 SHALL 保留服务端 `traceId` 以便排障

#### Scenario: 重试过程中防止重复请求

- **WHEN** 用户触发原位重试且请求尚未完成
- **THEN** 重试控件 SHALL 展示加载或禁用状态
- **AND** 页面 SHALL NOT 并发提交同一重试请求

#### Scenario: 结构化游标错误从第一页恢复

- **WHEN** 档案搜索、档案管理或案卷列表的带游标请求返回 `HttpClientError`
- **AND** `fieldViolations` 中存在 `field = cursor` 的错误
- **THEN** 页面 SHALL 显示“数据已变化，将从第一页重新加载”的可执行原因
- **AND** 页面 SHALL 保留草稿条件、已有结果、已提交查询、排序和页大小
- **AND** 重试 SHALL 使用失败时相同的已提交查询、排序和页大小，并清除 `cursor` 从第一页加载
- **AND** 页面 SHALL NOT 通过错误自由文本判断游标是否失效

#### Scenario: 普通读取错误保留失败请求

- **WHEN** 带游标的读取请求因网络错误或非游标字段的服务端错误失败
- **THEN** 重试 SHALL 保留失败请求中的原 `cursor`、已提交查询、排序和页大小
- **AND** 错误状态 SHALL 与已有结果或摘要同时显示
