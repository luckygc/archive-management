# 候选工程规范分析与人工确认清单

本文用于分析外部工程规范是否适合进入当前档案管理系统。结论默认不是强制项目规则；只有人工确认后，才把规则分别固化到 `AGENTS.md`、`openspec/specs/*` 或更细的 `docs/standards/*.md`。

## 判断原则

- 项目已有真相源优先：API 合同看 `openspec/specs/api-contract/spec.md`，档案元数据、动态表、逻辑删除和项目表命名看 `openspec/specs/archive-metadata/spec.md`，协作、架构和工具链规则看 `AGENTS.md`。
- 外部规范只能作为输入，不整套照搬；凡是和现有 OpenSpec、实际工具链或项目技术选型冲突的条款，先列为待确认。
- 工具链按当前仓库实际能力收口：前端使用 Vite+ 的 `vp lint`、`vp test`、`vp build`，后端使用 Maven、JDK 25、Spotless 和 `google-java-format` AOSP。
- 后续落地时最小化：先固化会减少歧义和返工的规则，不一次性引入 P3C、Checkstyle、SpotBugs、PMD、Error Prone、ESLint、Prettier 等完整工具堆栈。

## 总体建议

| 领域               | 建议结论                                                                                            | 说明                                                                                                         |
| ------------------ | --------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------ |
| Java               | 保留当前 Spotless + `google-java-format` AOSP；阿里 Java 手册只作参考                               | 当前仓库已经通过 Spotless 固化格式、导入和换行；阿里手册里的 MySQL、分层和工具建议不能整套套用               |
| Spring Boot        | 保留现有单体分层、ProblemDetail、事务边界和框架原生能力优先原则                                     | Spring 官方文档用于确认框架行为；项目规范应描述本项目的 Controller、Service、Repository、DTO 和事务边界      |
| React / TypeScript | 保留 React 官方 Hooks 规则；风格与检查以 Vite+ 和现有 Ant Design 体系为准                           | Airbnb、Google TS、typescript-eslint、Prettier 可以参考，但不应替代当前 `vp lint` / `tsc` / Vite+            |
| PostgreSQL         | 重点参考 Pigsty PostgreSQL Convention 2024 和 PostgreSQL 官方反模式文档，但必须按现有 OpenSpec 校准 | Pigsty 适合作为命名、SQL、索引和运维习惯蓝本；时间类型、CHECK 约束、表名前缀等要服从项目现有合同或走人工确认 |
| REST API           | 保留现有 Zalando 为主体、AIP-136 只作 custom method 扩展、错误响应使用 Spring ProblemDetail         | 这已经写入 `api-contract`，不建议再引入另一套响应包装或错误模型                                              |
| OpenAPI            | 建议作为接口说明和验收产物，但暂不因为规范文档而立即引入 springdoc 依赖                             | 引入前需要确认 OpenAPI 版本、生成方式、示例覆盖度和 CI 检查                                                  |
| 工程协作           | EditorConfig 可保留；Conventional Commits、SemVer、Keep a Changelog 待确认                          | 当前项目未正式发布前，不宜过早把版本和 changelog 流程做成强制门禁                                            |

## 1. Java 规范

### 候选来源

- [阿里巴巴 Java 开发手册](https://alibaba.github.io/Alibaba-Java-Coding-Guidelines/)
- [Alibaba P3C](https://github.com/alibaba/p3c)
- [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html)
- [google-java-format](https://github.com/google/google-java-format)

### 建议保留

- Java 格式化继续以 Spotless 调用 `google-java-format` AOSP 为唯一真相源。
- 导入顺序、移除未使用导入、UTF-8 和 UNIX line endings 继续由 Spotless 管理。
- 阿里 Java 手册中的命名、异常、日志、并发、集合和可读性建议可以作为人工 review 参考。

### 不建议直接采用

- 不把阿里手册里的 MySQL、分库分表或公司内部工程假设搬进 PostgreSQL-only 项目。
- 不立即把 P3C、Checkstyle、SpotBugs、PMD、Error Prone 全部加入 CI；这些工具会产生大量规则重叠和历史债务噪音。
- 不把 Google Java Style 手工格式规则写入项目文档；项目已有自动格式化工具，人工只需要知道运行入口。

### 待人工确认

| 编号 | 待确认项                                  | 默认建议                                                 |
| ---- | ----------------------------------------- | -------------------------------------------------------- |
| J-1  | 是否引入 P3C 或 SpotBugs 作为新增 CI 门禁 | 暂不引入，先保持 Spotless、编译、测试和 ArchUnit         |
| J-2  | 是否单独编写 `02-java-standard.md`        | 可以写，但只记录项目差异和执行入口，不复制通用 Java 手册 |

## 2. Spring Boot / Spring 规范

### 候选来源

- [Spring 官方站点](https://spring.io/)
- [Spring Boot Reference Documentation](https://docs.spring.io/spring-boot/reference/)
- [Spring Framework Reference Documentation](https://docs.spring.io/spring-framework/reference/)

### 建议保留

- Controller 只负责 HTTP 入参、认证上下文读取、响应 DTO 和错误映射，不承载业务编排。
- Service 作为业务编排和事务边界；只有确实涉及写入、状态变更、令牌消费或一致性要求时才开启事务。
- Repository / Mapper 只负责数据访问；固定 CRUD 表优先 Jakarta Data Repository，动态表、复杂 SQL、批处理和报表使用 MyBatis。
- 错误响应继续使用 Spring `ProblemDetail` / RFC 9457，并带项目扩展字段。
- Spring Session、Cache、Quartz、Modulith 等基础设施优先使用框架原生 AutoConfiguration 和标准配置，不新增项目级 adapter。

### 不建议直接采用

- 不引入“统一 Controller 基类”“统一 Service 模板”“统一 Repository adapter”这类为了规范而新增的抽象。
- 不把查询参数校验这类纯校验方法放进事务。
- 不在长事务内执行 HTTP、SFTP、NAS、Excel 解析或大文件处理。

### 待人工确认

| 编号 | 待确认项                                 | 默认建议                                                                |
| ---- | ---------------------------------------- | ----------------------------------------------------------------------- |
| S-1  | 是否新增 `03-spring-boot-standard.md`    | 可以新增，但应从 `AGENTS.md` 抽取项目专属规则，避免重复 Spring 官方文档 |
| S-2  | 是否强制所有查询方法都标注 readOnly 事务 | 不一刀切；高风险查询路径和 Repository 查询优先标注                      |

## 3. React / TypeScript 规范

### 候选来源

- [React Rules of Hooks](https://react.dev/reference/rules/rules-of-hooks)
- [React Reference](https://react.dev/reference/react)
- [Airbnb JavaScript Style Guide](https://javascript.airbnb.tech/)
- [Google TypeScript Style Guide](https://google.github.io/styleguide/tsguide.html)
- [typescript-eslint](https://typescript-eslint.io/)
- [ESLint](https://eslint.org/)
- [Prettier](https://prettier.io/)

### 建议保留

- React 官方 Hooks 规则作为正确性底线：Hooks 不放在循环、条件、嵌套函数或异常控制块中。
- TypeScript 类型以项目 API 合同为准，不靠前端猜字段、猜状态码或自行拼接非合同路径。
- 前端检查继续使用 `vp lint`、`vp test`、`vp build` 和 `tsc --noEmit`。
- Ant Design 相关用法优先查项目内 Ant Design CLI、本地文档和已安装文档；不足时再查 Context7。
- 表单、表格、状态归属继续遵守根 `AGENTS.md`：同一字段或表格状态只能有一个状态源。

### 不建议直接采用

- 不把 Airbnb 或 Google TS Style 整套变成强制规则；两者和 Vite+、Oxfmt/Oxlint、Ant Design 项目写法可能有冲突。
- 不在已有 Vite+ 统一链路外另起 Prettier / ESLint 门禁，除非明确证明 Vite+ 不能覆盖当前问题。
- 不为后台系统写营销式页面结构、装饰 hero 或大段说明型文案。

### 待人工确认

| 编号 | 待确认项                                       | 默认建议                                                |
| ---- | ---------------------------------------------- | ------------------------------------------------------- |
| F-1  | 是否需要独立 `04-react-typescript-standard.md` | 可以写，但应以 Vite+、Ant Design 和当前状态管理约束为主 |
| F-2  | 是否引入 Prettier 或 ESLint 配置文件           | 暂不引入；先使用 Vite+ 现有能力                         |

## 4. PostgreSQL / SQL / DDL 规范

### 候选来源

- [Pigsty PostgreSQL Convention 2024](https://pigsty.io/blog/pg/pg-convention/)
- [Pigsty PostgreSQL 博客目录](https://pigsty.io/blog/pg/)
- [PostgreSQL 官方文档](https://www.postgresql.org/docs/)
- [PostgreSQL Wiki: Don't Do This](https://wiki.postgresql.org/wiki/Don%27t_Do_This)
- [depesz: Don't do these things in PostgreSQL](https://www.depesz.com/2020/01/28/dont-do-these-things-in-postgresql/)

### 建议保留

- PostgreSQL 是唯一优先目标；项目自有 DDL、索引、约束、查询和迁移允许围绕 PostgreSQL 优化。
- 表名、列名、索引名、约束名、别名统一小写 snake_case，不使用双引号或反引号。
- 项目自有表名保留 `am_模块_表名` 语义；第三方框架原生表保留上游默认命名。
- 逻辑删除表的唯一性使用部分唯一索引，只约束未删除记录。
- 动态档案表、动态字段、动态 DDL、唯一规则和全文/模糊检索合同以 `archive-metadata` 和 `archive-record-search` OpenSpec 为准。
- 高频精确筛选字段应有普通索引；普通数据库内前后模糊匹配优先使用 PostgreSQL `pg_trgm` + GIN + `ILIKE`。
- Flyway 管理项目 DDL；正式发布前可以按目标结构直接维护，不为未发布旧结构保留兼容迁移包袱。
- SQL 不硬编码 `public` schema，不写 `public.` 或 `table_schema = 'public'` 这类假设。

### 和现有项目冲突的候选规则

| 候选规则                                      | 当前项目口径                                                                         | 建议                                                                |
| --------------------------------------------- | ------------------------------------------------------------------------------------ | ------------------------------------------------------------------- |
| 业务时间统一 `timestamptz`                    | `archive-metadata` 当前要求项目自有业务时间字段使用无时区 `timestamp`                | 待人工确认；若改用 `timestamptz`，必须先改 OpenSpec 再改 DDL 和代码 |
| 状态字段 `varchar + check`                    | `archive-metadata` 当前要求项目自有表不为枚举、状态或数值范围创建数据库 `CHECK` 约束 | 默认不采用；校验放应用层、字典或配置层                              |
| 主键统一 bigint                               | 当前多数资源使用 Long/数字 ID，但动态表 `id` 引用统一档案记录主表 ID                 | 保留方向，但不要写成不允许 UUID 或第三方框架表例外                  |
| 索引统一 `idx_表_字段`、唯一索引 `uk_表_字段` | 当前项目要求索引、约束和序列名称跟随表名保留 `am_模块_` 语义                         | 使用项目命名前缀，不照搬短前缀                                      |
| 大表建索引使用 `CONCURRENTLY`                 | 对生产大表合理，但 Flyway 事务和执行方式需要单独设计                                 | 写入 DDL 标准时单列章节说明，不作为所有索引默认                     |

### 待人工确认

| 编号 | 待确认项                                                                                   | 默认建议                                           |
| ---- | ------------------------------------------------------------------------------------------ | -------------------------------------------------- |
| PG-1 | 项目自有业务时间字段继续 `timestamp`，还是切到 `timestamptz`                               | 继续按现有 OpenSpec 使用 `timestamp`               |
| PG-2 | 是否允许项目自有状态/枚举字段使用数据库 `CHECK`                                            | 继续禁止，避免应用字典和数据库约束双状态源         |
| PG-3 | 是否拆出 `07-postgresql-standard.md`、`08-sql-standard.md`、`09-ddl-migration-standard.md` | 建议拆，但只承载技术规则；业务合同继续在 OpenSpec  |
| PG-4 | `CREATE INDEX CONCURRENTLY` 的使用规则是否现在就固化                                       | 建议先写原则，等出现生产大表迁移场景再细化执行模板 |

## 5. REST API / HTTP API 规范

### 候选来源

- [Zalando RESTful API Guidelines](https://opensource.zalando.com/restful-api-guidelines/)
- [Microsoft REST API Guidelines](https://github.com/microsoft/api-guidelines)
- [Microsoft REST API Design Guidance](https://microsoft.github.io/code-with-engineering-playbook/design/design-patterns/rest-api-design-guidance/)
- [Google API Design Guide](https://cloud.google.com/apis/design)
- [Google AIP](https://aip.dev/)
- [Google AIP-136 Custom Methods](https://google.aip.dev/136)

### 建议保留

- 普通资源、URL、HTTP 方法、JSON 响应、分页、兼容性以 Zalando RESTful API Guidelines 为主体参考。
- 标准 CRUD 难以表达的业务动作只使用 Google AIP-136 custom method，例如 `POST /api/v1/archive-records/{archiveRecord}:lock`。
- 长耗时导入、导出、批处理、外部同步、AI/OCR 等任务参考 Microsoft Azure long-running operation 模式，返回 `202 Accepted`、`Operation-Location` 和可轮询 job 资源。
- 错误响应使用 Spring `ProblemDetail` / RFC 9457，不采用统一 `Result<T>` 包装，也不让前端解析异常类名、HTML 或纯文本。
- 分页继续使用项目自有 `CollectionResponse<T>`、`OffsetPageResponse<T>`、`CursorPageResponse<T>`，列表字段固定为 `items`。

### 不建议直接采用

- 不把 Google AIP 整套资源名、`name` 字段、分页字段或 Google RPC 错误模型直接搬进项目。
- 不在项目里混用 `page/size`、`limit/offset`、`pageToken`、`nextPageToken` 等多套分页语言。
- 不使用 `/submit`、`/_submit`、`/validate_token`、`/validateToken` 这类动作路径段。

### 待人工确认

| 编号  | 待确认项                                    | 默认建议                                                         |
| ----- | ------------------------------------------- | ---------------------------------------------------------------- |
| API-1 | 是否还需要单独 `05-rest-api-standard.md`    | 可以新增，但应引用 `api-contract`，不要复制一份会漂移的 API 规则 |
| API-2 | 动作接口示例是否按实际业务能力补入 OpenSpec | 等具体业务出现时再进对应业务规格                                 |

## 6. OpenAPI 规范

### 候选来源

- [OpenAPI Initiative](https://www.openapis.org/)
- [OpenAPI Specification](https://swagger.io/specification/)
- [OpenAPI GitHub](https://github.com/OAI/OpenAPI-Specification)

### 建议保留

- OpenAPI 可以作为接口说明、客户端生成、契约测试和审查材料。
- 每个项目自有接口应有 summary、请求示例、成功响应示例、错误响应 schema、权限说明和枚举含义。
- 错误响应 schema 必须和项目 `ProblemDetail` 扩展字段一致。
- 分页响应 schema 必须和 `api-contract` 的统一分页对象一致。

### 不建议直接采用

- 不为了“应该有 OpenAPI”就立即引入 springdoc 或注解改造；仓库当前 `server/pom.xml` 未接 OpenAPI 依赖。
- 不在 Controller 上散写和 OpenSpec、DTO 实现可能漂移的大量注解，除非确定生成链路和 CI 校验。

### 待人工确认

| 编号 | 待确认项                                             | 默认建议                                    |
| ---- | ---------------------------------------------------- | ------------------------------------------- |
| OA-1 | OpenAPI 使用 3.1 还是跟随工具链支持版本              | 待 springdoc 或生成工具选型后确认           |
| OA-2 | OpenAPI 由代码注解生成、OpenSpec 转换，还是手写 yaml | 倾向先不定；等 API 稳定和生成链路明确后再选 |
| OA-3 | 是否把 OpenAPI 示例覆盖率做成 CI 门禁                | 暂不作为第一阶段门禁                        |

## 7. 工程协作规范

### 候选来源

- [Conventional Commits](https://www.conventionalcommits.org/)
- [Semantic Versioning](https://semver.org/)
- [EditorConfig](https://editorconfig.org/)
- [Keep a Changelog](https://keepachangelog.com/)

### 建议保留

- EditorConfig 可以作为编辑器基础格式辅助，但格式化真相源仍是后端 Spotless 和前端 Vite+。
- 代码评审至少关注编译、lint、测试、架构边界、迁移和 API 合同漂移。
- 文档和规则落点继续区分：业务字段、流程、状态机、接口行为进 OpenSpec；协作方式、代码风格、架构边界和工具链进 `AGENTS.md`。

### 待人工确认

| 编号 | 待确认项                               | 默认建议                                               |
| ---- | -------------------------------------- | ------------------------------------------------------ |
| E-1  | 是否强制 Conventional Commits          | 可以作为推荐，不急于做提交门禁                         |
| E-2  | 是否在未正式发布前使用 SemVer          | 暂不强制；发布流程明确后再定                           |
| E-3  | 是否维护 Keep a Changelog              | 等出现对外版本发布后再引入                             |
| E-4  | 是否新增 `12-code-review-checklist.md` | 建议新增，优先覆盖本项目高风险项，而不是泛化 checklist |

## 建议的文档落点

如果人工确认后要拆标准文档，建议按优先级逐步建立，而不是一次性写满 12 份。

| 优先级 | 文件                                             | 建议内容                                                            |
| ------ | ------------------------------------------------ | ------------------------------------------------------------------- |
| P0     | `docs/standards/07-postgresql-standard.md`       | PostgreSQL 命名、类型、索引、事务、运维底线，明确和 OpenSpec 的关系 |
| P0     | `docs/standards/08-sql-standard.md`              | SQL 写法、动态 SQL、分页、排序、schema、`select *`、count、模糊检索 |
| P0     | `docs/standards/09-ddl-migration-standard.md`    | Flyway、DDL 分阶段、大表索引、动态 DDL、第三方表例外                |
| P1     | `docs/standards/05-rest-api-standard.md`         | 只引用和解释 `api-contract`，不复制完整合同                         |
| P1     | `docs/standards/12-code-review-checklist.md`     | 面向本项目的 review 清单                                            |
| P2     | `docs/standards/02-java-standard.md`             | Java 项目差异、格式化入口、nullness、字符串工具、异常和日志         |
| P2     | `docs/standards/03-spring-boot-standard.md`      | 单体分层、事务、ProblemDetail、框架原生能力优先                     |
| P2     | `docs/standards/04-react-typescript-standard.md` | Vite+、React Hooks、Ant Design、状态源、表单和表格规则              |
| P3     | `docs/standards/06-openapi-standard.md`          | 等 OpenAPI 生成链路确定后再写                                       |
| P3     | `docs/standards/01-engineering-standard.md`      | 只放跨语言协作原则，避免和 `AGENTS.md` 重复                         |
| P3     | `docs/standards/10-security-standard.md`         | 等认证、权限、审计和密钥管理边界稳定后单独设计                      |
| P3     | `docs/standards/11-logging-audit-standard.md`    | 等业务审计表和操作流水规格稳定后单独设计                            |

## 第一轮人工确认清单

建议先确认这些会影响后续代码和迁移的关键决策：

| 编号 | 问题                                                                                                              | 默认建议 |
| ---- | ----------------------------------------------------------------------------------------------------------------- | -------- |
| C-1  | PG 时间类型是否继续使用当前 OpenSpec 的无时区 `timestamp`                                                         | 是       |
| C-2  | 项目自有枚举、状态和范围是否继续禁止数据库 `CHECK`                                                                | 是       |
| C-3  | 前端是否继续以 Vite+ 为唯一 lint / format / build 入口，不新增 Prettier / ESLint 门禁                             | 是       |
| C-4  | Java 是否只保留 Spotless + `google-java-format` AOSP，不新增 P3C / Checkstyle / PMD / SpotBugs / Error Prone 门禁 | 是       |
| C-5  | REST API 是否继续以 `api-contract` 为唯一真相源，标准文档只解释不复制                                             | 是       |
| C-6  | OpenAPI 是否先作为待选生成产物，不立即引入依赖和注解改造                                                          | 是       |
| C-7  | 是否优先写 PG、SQL、DDL 三份标准，再写 Java、Spring、前端等标准                                                   | 是       |
