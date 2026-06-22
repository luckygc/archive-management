# WSL PostgreSQL 安装 pg_textsearch

本文记录在 WSL 的 PostgreSQL 中安装 `pg_textsearch` 的步骤。当前本机环境已确认是 Ubuntu 24.04 noble、PostgreSQL 18，因此默认命令以 PG18 为准。

## 适用前提

- PostgreSQL 已通过 PGDG 或系统包安装。
- 当前主版本是 PostgreSQL 18。
- 需要能执行 `sudo`，因为安装扩展包、修改 `shared_preload_libraries` 和重启 PostgreSQL 都需要系统权限。

先确认当前版本和集群状态：

```bash
psql --version
pg_config --version
pg_lsclusters
```

## 使用 pig 安装

安装 `pig`：

```bash
curl -fsSL https://repo.pigsty.io/pig | bash
```

如果访问 Cloudflare CDN 较慢，可以改用国内镜像：

```bash
curl -fsSL https://repo.pigsty.cc/pig | bash
```

添加 PGDG 和 Pigsty 扩展仓库：

```bash
pig repo add pigsty pgdg -u
```

安装当前 PG18 对应的 `pg_textsearch`：

```bash
pig ext install -y pg_textsearch -v 18
```

如果将来切到 PG17，对应命令是：

```bash
pig ext install -y pg_textsearch -v 17
```

## 使用 apt 安装

`pig` 底层也是使用系统包管理器。仓库配置好以后，可以直接安装对应 DEB 包。

PG18：

```bash
sudo apt update
sudo apt install -y postgresql-18-textsearch
```

PG17：

```bash
sudo apt update
sudo apt install -y postgresql-17-textsearch
```

## 配置预加载

`pg_textsearch` 必须加入 `shared_preload_libraries`，并重启 PostgreSQL 后才能在数据库中 `CREATE EXTENSION`。

先查看当前值，避免覆盖已有配置：

```bash
sudo -u postgres psql -Atc "SHOW shared_preload_libraries;"
```

如果当前为空：

```bash
sudo -u postgres psql -c "ALTER SYSTEM SET shared_preload_libraries = 'pg_textsearch';"
```

如果当前已有值，例如 `pg_stat_statements`，需要合并：

```bash
sudo -u postgres psql -c "ALTER SYSTEM SET shared_preload_libraries = 'pg_stat_statements,pg_textsearch';"
```

如果当前已有 `pg_search`，先确认是否还需要继续使用。`pg_textsearch` 与 `pg_search` 的 `bm25` access method 冲突，同一个数据库里不要同时启用二者。当前项目库如果改用 `pg_textsearch`，更推荐只保留：

```bash
sudo -u postgres psql -c "ALTER SYSTEM SET shared_preload_libraries = 'pg_textsearch';"
```

重启对应集群：

```bash
sudo pg_ctlcluster 18 main restart
```

如果本机没有 `pg_ctlcluster`，再使用服务命令：

```bash
sudo service postgresql restart
```

## 启用扩展

在每个需要使用全文检索的数据库中执行一次：

```bash
sudo -u postgres psql -d <数据库名> -c "CREATE EXTENSION IF NOT EXISTS pg_textsearch;"
sudo -u postgres psql -d <数据库名> -c "\dx pg_textsearch"
```

## 验证 BM25 索引

```sql
CREATE TABLE documents (
  id bigserial PRIMARY KEY,
  content text
);

CREATE INDEX documents_content_bm25_idx
ON documents USING bm25 (content)
WITH (text_config = 'simple');

SELECT *
FROM documents
ORDER BY content <@> 'archive management'
LIMIT 5;
```

## 常见问题排查

查看集群和端口：

```bash
pg_lsclusters
pg_isready -h localhost -p 5432
pg_isready -h localhost -p 5433
```

查看具体集群服务：

```bash
systemctl status postgresql@18-main
```

查看 PostgreSQL 日志：

```bash
sudo tail -n 120 /var/log/postgresql/postgresql-18-main.log
```

如果 `service postgresql status` 显示 `active (exited)`，不能说明数据库进程已经运行。Debian/Ubuntu 的 `postgresql.service` 是总入口，真实状态要以 `pg_lsclusters` 里的 `18/main` 是否 `online` 为准。

如果集群端口不是 `5432`，应用配置和 `pg_isready` 也要使用实际端口。例如当前集群如果显示 `5433`：

```bash
pg_isready -h localhost -p 5433
```

如果重启失败并出现类似错误：

```text
FATAL: could not access file "pg_search,pg_textsearch": No such file or directory
```

说明 PostgreSQL 把 `pg_search,pg_textsearch` 当成了一个库名，而不是两个库名。常见原因是通过 `ALTER SYSTEM` 写入时多套了一层双引号，导致 `postgresql.auto.conf` 中的值类似：

```text
shared_preload_libraries = '"pg_search,pg_textsearch"'
```

修复方式是去掉多余双引号，并按目标选择一个或多个库。当前项目库如果改用 `pg_textsearch`，优先只保留 `pg_textsearch`。在数据库可启动时执行：

```bash
sudo -u postgres psql -c "ALTER SYSTEM SET shared_preload_libraries = 'pg_textsearch';"
sudo pg_ctlcluster 18 main restart
```

只有确认需要在不同数据库中分别使用 `pg_search` 和 `pg_textsearch` 时，才写成正常的逗号分隔列表：

```bash
sudo -u postgres psql -c "ALTER SYSTEM SET shared_preload_libraries = 'pg_search,pg_textsearch';"
sudo pg_ctlcluster 18 main restart
```

如果数据库已经无法启动，需要手动编辑自动配置文件：

```bash
sudo cp /var/lib/postgresql/18/main/postgresql.auto.conf /var/lib/postgresql/18/main/postgresql.auto.conf.bak.$(date +%Y%m%d%H%M%S)
sudoedit /var/lib/postgresql/18/main/postgresql.auto.conf
sudo pg_ctlcluster 18 main start
```

确认修复后：

```bash
pg_lsclusters
pg_isready -h localhost -p 5433
```

## 注意事项

- `pg_textsearch` 的 `bm25` access method 与 `pg_search`、`vchord_bm25` 冲突，不要安装到同一个数据库中。
- Pigsty 当前提供的 `pg_textsearch` 预构建包覆盖 PG17 和 PG18；其他 PostgreSQL 主版本需要确认是否已有包，或从源码构建。
- 从源码构建时可以使用上游仓库的 `make && sudo make install`，但本地开发优先使用 `pig` 或 `apt` 安装预构建包，减少工具链差异。

## 参考资料

- Pigsty PIG 文档：https://pigsty.io/docs/pig/
- Pigsty `pg_textsearch` 扩展目录：https://pigsty.io/ext/e/pg_textsearch/
- `pg_textsearch` 上游仓库：https://github.com/timescale/pg_textsearch
