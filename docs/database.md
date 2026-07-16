# 数据库手册

项目数据库以 PostgreSQL 为唯一优先目标。项目自有 DDL、索引、约束、查询和迁移可以围绕 PostgreSQL 能力优化，不为其他数据库做兼容折中。

## 连接配置

默认本地连接：

```yaml
spring:
    datasource:
        url: jdbc:postgresql://localhost:5433/archive_management
        driver-class-name: org.postgresql.Driver
        username: postgres
        password:
```

生产环境通过外部配置覆盖连接地址、账号、密码和连接池参数。

## 命名规则

项目自有表统一使用：

```text
am_模块_表名
```

示例：

- `am_archive_item`
- `am_archive_volume`
- `am_authentication_event`
- `am_authorization_role`

SQL 标识符统一使用小写 snake_case，不使用双引号或反引号包裹。只有表达式或确需更名时才写 alias，alias 也使用小写 snake_case。

第三方框架原生表保留上游命名：

- Spring Session：`SPRING_SESSION`
- Quartz：`QRTZ_*`
- Flowable：`ACT_*`
- Spring Modulith 事件发布注册表：由框架管理

## Flyway

迁移目录：

```text
server/src/main/resources/db/migration
```

样例数据目录：

```text
server/src/main/resources/db/sample
```

默认配置：

```yaml
spring:
    flyway:
        enabled: true
        locations:
            - classpath:db/migration
        baseline-on-migrate: true
        baseline-version: 0
        validate-on-migrate: true
        clean-disabled: true
```

规则：

- 结构迁移默认只加载 `classpath:db/migration`。
- 本地演示需要样例数据时，在 `application-local.yaml` 追加 `classpath:db/sample`。
- 生产环境保持 `clean-disabled=true`。
- 已发布迁移不得修改历史内容；当前未正式发布前可按目标结构直接维护。
- 迁移脚本不假定 schema 固定为 `public`。

## 当前迁移范围

已纳入迁移的主要能力：

- Spring Session 表。
- Quartz 表。
- Flowable common、process engine 和 history 表。
- 文件存储表。
- 用户、CAP 和登录相关表。
- 档案主表、案卷、条目、动态分类表元数据、规则和审计相关表。
- 认证事件和登录失败限制。
- 授权权限和档案数据范围。
- 文件短链表。
- 组织部门。
- 档案治理、本体和本地规则 foundation。

## 固定表和动态表

固定表用于稳定业务对象：

- 全宗。
- 分类方案。
- 分类节点。
- 字段定义。
- 字段布局。
- 档案条目。
- 案卷。
- 电子文件。
- 审计和规则追踪。

动态表用于不同档案分类的动态字段。字段定义、动态表名、列名、索引和唯一规则由档案元数据服务维护，不通过手工 SQL 直接绕过业务服务修改。

动态表规则：

- 动态表名和动态列名必须通过合法性校验。
- 删除标记和维护时间字段固定存在。
- 分类唯一规则通过动态唯一索引实现。
- 动态字段用于查询时，先按字段定义判断是否允许参与筛选。

## 持久化入口

| 场景 | 入口 | 说明 |
| --- | --- | --- |
| 固定 CRUD 表 | Jakarta Data Repository | 自定义方法显式标注 `@Find`、`@Insert`、`@Update`、`@Delete`、`@Query` 或 Hibernate `@HQL` |
| 动态表和复杂 SQL | MyBatis Mapper | XML SQL 放在 `server/src/main/resources/mapper` |
| 文件内容 | `FileStorageService` | 数据库只保存元数据和 object key |

MyBatis 写入不会触发 Hibernate 时间戳注解或无状态会话审计拦截器，需要 SQL 或调用方显式维护审计字段。

## 本地重建

推荐方式是删除并重建本地数据库：

```sql
drop database archive_management;
create database archive_management;
```

然后启动后端，让 Flyway 执行迁移。需要样例数据时，在本地配置中加入 `classpath:db/sample`。

不建议在共享环境使用 Flyway clean。确需本地临时 clean 时，同时需要：

- 外部配置允许 Flyway clean。
- `archive.flyway.clean-before-migrate=true`。
- 明确数据库只属于当前本机开发者。

## 排障查询

查看迁移历史：

```sql
select installed_rank, version, description, type, script, checksum, success
from flyway_schema_history
order by installed_rank;
```

查看当前 schema：

```sql
select current_schema();
```

查看当前 search_path：

```sql
show search_path;
```

查看 PostgreSQL 扩展：

```sql
select extname from pg_extension order by extname;
```

全文检索 provider 为 `postgresql` 时，需要确认相关扩展和索引是否已经按迁移建立。

## 备份恢复

数据库备份必须和文件存储备份配套。只恢复数据库而不恢复文件内容，会导致电子文件、短链和下载审计指向不存在的 object key。

最小恢复顺序：

1. 恢复 PostgreSQL。
2. 恢复 S3 兼容对象存储 bucket。
3. 恢复部署配置。
4. 启动应用并检查 Flyway、健康端点和核心页面。
