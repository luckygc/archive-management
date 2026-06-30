create table am_archive_fonds
(
    id          bigserial primary key,
    fonds_code  varchar(100) not null,
    fonds_name  varchar(255) not null,
    enabled     boolean      not null default true,
    sort_order  integer      not null default 0,
    deleted_flag boolean     not null default false,
    version      integer     not null default 0,
    created_by  bigint,
    created_at  timestamp    not null default localtimestamp,
    updated_by  bigint,
    updated_at  timestamp    not null default localtimestamp
);

create extension if not exists pg_trgm;

create unique index uk_am_archive_fonds_code_active
    on am_archive_fonds (fonds_code)
    where deleted_flag = false;
create index idx_am_archive_fonds_sort_active
    on am_archive_fonds (sort_order, id)
    where deleted_flag = false;

comment on table am_archive_fonds is '档案全宗表';
comment on column am_archive_fonds.id is '主键';
comment on column am_archive_fonds.fonds_code is '全宗编码';
comment on column am_archive_fonds.fonds_name is '全宗名称';
comment on column am_archive_fonds.enabled is '是否启用';
comment on column am_archive_fonds.sort_order is '排序字段';
comment on column am_archive_fonds.deleted_flag is '删除标记';
comment on column am_archive_fonds.version is '乐观锁版本号';
comment on column am_archive_fonds.created_by is '创建人用户 ID';
comment on column am_archive_fonds.created_at is '创建时间';
comment on column am_archive_fonds.updated_by is '更新人用户 ID';
comment on column am_archive_fonds.updated_at is '更新时间';

create table am_archive_category
(
    id                bigserial primary key,
    parent_id         bigint references am_archive_category (id),
    category_code     varchar(100) not null,
    category_name     varchar(255) not null,
    management_mode   varchar(30)  not null default 'ITEM_ONLY',
    volume_table_name varchar(100),
    item_table_name   varchar(100),
    volume_physical_table_name varchar(100),
    item_physical_table_name   varchar(100),
    table_status      varchar(30)  not null default 'NOT_BUILT',
    built_at          timestamp,
    enabled           boolean      not null default true,
    sort_order        integer      not null default 0,
    deleted_flag      boolean      not null default false,
    version           integer      not null default 0,
    created_by        bigint,
    created_at        timestamp    not null default localtimestamp,
    updated_by        bigint,
    updated_at        timestamp    not null default localtimestamp
);

create unique index uk_am_archive_category_code_active
    on am_archive_category (category_code)
    where deleted_flag = false;
create index idx_am_archive_category_sort_active
    on am_archive_category (parent_id, sort_order, id)
    where deleted_flag = false;

comment on table am_archive_category is '档案分类表';
comment on column am_archive_category.id is '主键';
comment on column am_archive_category.parent_id is '父级档案分类 ID';
comment on column am_archive_category.category_code is '档案分类编码';
comment on column am_archive_category.category_name is '档案分类名称';
comment on column am_archive_category.management_mode is '管理模式：ITEM_ONLY 只按条目管理，VOLUME_ITEM 按案卷和卷内条目管理';
comment on column am_archive_category.volume_table_name is '案卷层级动态记录表名';
comment on column am_archive_category.item_table_name is '卷内条目层级动态记录表名';
comment on column am_archive_category.volume_physical_table_name is '案卷层级动态实物信息表名';
comment on column am_archive_category.item_physical_table_name is '卷内条目层级动态实物信息表名';
comment on column am_archive_category.table_status is '动态表状态：NOT_BUILT 未建表，BUILT 已建表';
comment on column am_archive_category.built_at is '最近建表时间';
comment on column am_archive_category.enabled is '是否启用';
comment on column am_archive_category.sort_order is '排序字段';
comment on column am_archive_category.deleted_flag is '删除标记';
comment on column am_archive_category.version is '乐观锁版本号';
comment on column am_archive_category.created_by is '创建人用户 ID';
comment on column am_archive_category.created_at is '创建时间';
comment on column am_archive_category.updated_by is '更新人用户 ID';
comment on column am_archive_category.updated_at is '更新时间';

create table am_archive_field
(
    id                bigserial primary key,
    category_id       bigint       not null references am_archive_category (id),
    archive_level     varchar(30)  not null default 'ITEM',
    field_scope       varchar(30)  not null default 'METADATA',
    field_code        varchar(80)  not null,
    field_name        varchar(255) not null,
    field_type        varchar(30)  not null,
    column_name       varchar(100) not null,
    text_length       integer,
    decimal_precision integer,
    decimal_scale     integer,
    edit_control      varchar(30)  not null default 'INPUT',
    list_visible      boolean      not null default true,
    list_width        integer,
    list_sort_order   integer      not null default 0,
    detail_visible    boolean      not null default true,
    detail_col_span   integer      not null default 1,
    detail_sort_order integer      not null default 0,
    edit_visible      boolean      not null default true,
    edit_col_span     integer      not null default 1,
    edit_sort_order   integer      not null default 0,
    exact_searchable  boolean      not null default false,
    enabled           boolean      not null default true,
    sort_order        integer      not null default 0,
    deleted_flag      boolean      not null default false,
    version           integer      not null default 0,
    created_by        bigint,
    created_at        timestamp    not null default localtimestamp,
    updated_by        bigint,
    updated_at        timestamp    not null default localtimestamp
);

create unique index uk_am_archive_field_code_active
    on am_archive_field (category_id, archive_level, field_scope, field_code)
    where deleted_flag = false;
create index idx_am_archive_field_category_active
    on am_archive_field (category_id, archive_level, field_scope, sort_order, id)
    where deleted_flag = false;

comment on table am_archive_field is '档案分类字段定义表';
comment on column am_archive_field.id is '主键';
comment on column am_archive_field.category_id is '档案分类 ID';
comment on column am_archive_field.archive_level is '字段适用层级：volume 案卷，item 卷内条目';
comment on column am_archive_field.field_scope is '字段域：metadata 电子字段，physical 实物信息字段';
comment on column am_archive_field.field_code is '字段编码';
comment on column am_archive_field.field_name is '字段名称';
comment on column am_archive_field.field_type is '字段类型：text 文本，integer 整数，decimal 小数，date 日期，datetime 日期时间';
comment on column am_archive_field.column_name is '动态表列名';
comment on column am_archive_field.text_length is '文本长度';
comment on column am_archive_field.decimal_precision is '小数总位数';
comment on column am_archive_field.decimal_scale is '小数位数';
comment on column am_archive_field.edit_control is '编辑控件：input 单行输入，textarea 多行输入，number 数字输入，date 日期选择，datetime 日期时间选择';
comment on column am_archive_field.list_visible is '是否列表显示';
comment on column am_archive_field.list_width is '列表列宽';
comment on column am_archive_field.list_sort_order is '列表布局排序';
comment on column am_archive_field.detail_visible is '是否详情显示';
comment on column am_archive_field.detail_col_span is '详情跨列数';
comment on column am_archive_field.detail_sort_order is '详情布局排序';
comment on column am_archive_field.edit_visible is '是否编辑显示';
comment on column am_archive_field.edit_col_span is '编辑表单跨列数';
comment on column am_archive_field.edit_sort_order is '编辑布局排序';
comment on column am_archive_field.exact_searchable is '是否允许精确搜索';
comment on column am_archive_field.enabled is '是否启用';
comment on column am_archive_field.sort_order is '排序字段';
comment on column am_archive_field.deleted_flag is '删除标记';
comment on column am_archive_field.version is '乐观锁版本号';
comment on column am_archive_field.created_by is '创建人用户 ID';
comment on column am_archive_field.created_at is '创建时间';
comment on column am_archive_field.updated_by is '更新人用户 ID';
comment on column am_archive_field.updated_at is '更新时间';

create sequence am_archive_item_id_seq
    as bigint
    start with 1000000
    increment by 1000;

create sequence am_archive_volume_id_seq
    as bigint
    start with 1000000
    increment by 1000;

create table am_archive_volume
(
    id             bigint primary key default nextval('am_archive_volume_id_seq'),
    fonds_code     varchar(100) not null,
    fonds_name     varchar(255) not null,
    category_code  varchar(100) not null,
    category_name  varchar(255) not null,
    archive_no     varchar(100),
    electronic_status varchar(50)  not null,
    security_level varchar(50),
    sort_order     integer      not null default 0,
    display_order  integer      not null default 0,
    archived_at    timestamp,
    archive_year   integer      not null,
    random_bucket  smallint     not null default floor(random() * 10000)::smallint
        check (random_bucket >= 0 and random_bucket < 10000),
    locked_flag    boolean      not null default false,
    lock_reason    varchar(500),
    locked_by      bigint,
    locked_at      timestamp,
    deleted_flag   boolean      not null default false,
    deleted_at     timestamp,
    deleted_by     bigint,
    version        integer      not null default 0,
    created_by     bigint,
    created_at     timestamp    not null default localtimestamp,
    updated_by     bigint,
    updated_at     timestamp    not null default localtimestamp
);

create index idx_am_archive_volume_category_active
    on am_archive_volume (category_code, id)
    where deleted_flag = false;
create index idx_am_archive_volume_fonds_active
    on am_archive_volume (fonds_code, category_code)
    where deleted_flag = false;
create index idx_am_archive_volume_year_active
    on am_archive_volume (archive_year)
    where deleted_flag = false;
create index idx_am_archive_volume_random_bucket_active
    on am_archive_volume (random_bucket, id)
    where deleted_flag = false;
create index idx_am_archive_volume_sort_active
    on am_archive_volume (sort_order, id)
    where deleted_flag = false;
create unique index uk_am_archive_volume_category_archive_no_active
    on am_archive_volume (category_code, archive_no)
    where deleted_flag = false;
create index idx_am_archive_volume_created_at on am_archive_volume (created_at);
create index idx_am_archive_volume_deleted_at
    on am_archive_volume (deleted_at desc, id desc)
    where deleted_flag = true;

comment on table am_archive_volume is '档案案卷主表';
comment on column am_archive_volume.id is '主键';
comment on column am_archive_volume.fonds_code is '全宗编码';
comment on column am_archive_volume.fonds_name is '全宗名称';
comment on column am_archive_volume.category_code is '档案分类编码';
comment on column am_archive_volume.category_name is '档案分类名称';
comment on column am_archive_volume.archive_no is '档号';
comment on column am_archive_volume.electronic_status is '电子档案状态';
comment on column am_archive_volume.security_level is '密级';
comment on column am_archive_volume.sort_order is '排序字段';
comment on column am_archive_volume.archived_at is '归档时间';
comment on column am_archive_volume.archive_year is '年度';
comment on column am_archive_volume.random_bucket is '随机抽查辅助分桶';
comment on column am_archive_volume.locked_flag is '业务锁定标记';
comment on column am_archive_volume.lock_reason is '锁定原因';
comment on column am_archive_volume.locked_by is '锁定人用户 ID';
comment on column am_archive_volume.locked_at is '锁定时间';
comment on column am_archive_volume.deleted_flag is '删除标记';
comment on column am_archive_volume.deleted_at is '删除时间';
comment on column am_archive_volume.deleted_by is '删除人用户 ID';
comment on column am_archive_volume.version is '乐观锁版本号';
comment on column am_archive_volume.created_by is '创建人用户 ID';
comment on column am_archive_volume.created_at is '创建时间';
comment on column am_archive_volume.updated_by is '更新人用户 ID';
comment on column am_archive_volume.updated_at is '更新时间';

create table am_archive_item
(
    id             bigint primary key default nextval('am_archive_item_id_seq'),
    volume_id      bigint references am_archive_volume (id),
    fonds_code     varchar(100) not null,
    fonds_name     varchar(255) not null,
    category_code  varchar(100) not null,
    category_name  varchar(255) not null,
    archive_no     varchar(100),
    electronic_status varchar(50)  not null,
    security_level varchar(50),
    sort_order     integer      not null default 0,
    display_order  integer      not null default 0,
    archived_at    timestamp,
    archive_year   integer      not null,
    random_bucket  smallint     not null default floor(random() * 10000)::smallint
        check (random_bucket >= 0 and random_bucket < 10000),
    locked_flag    boolean      not null default false,
    lock_reason    varchar(500),
    locked_by      bigint,
    locked_at      timestamp,
    deleted_flag   boolean      not null default false,
    deleted_at     timestamp,
    deleted_by     bigint,
    version        integer      not null default 0,
    created_by     bigint,
    created_at     timestamp    not null default localtimestamp,
    updated_by     bigint,
    updated_at     timestamp    not null default localtimestamp
);

create index idx_am_archive_item_category_active
    on am_archive_item (category_code, id)
    where deleted_flag = false;
create index idx_am_archive_item_fonds_active
    on am_archive_item (fonds_code, category_code)
    where deleted_flag = false;
create index idx_am_archive_volume_data_active
    on am_archive_item (volume_id, display_order, id)
    where deleted_flag = false;
create index idx_am_archive_item_year_active
    on am_archive_item (archive_year)
    where deleted_flag = false;
create index idx_am_archive_item_random_bucket_active
    on am_archive_item (random_bucket, id)
    where deleted_flag = false;
create index idx_am_archive_item_sort_active
    on am_archive_item (sort_order, id)
    where deleted_flag = false;
create unique index uk_am_archive_item_category_archive_no_active
    on am_archive_item (category_code, archive_no)
    where deleted_flag = false;
create index idx_am_archive_item_created_at on am_archive_item (created_at);
create index idx_am_archive_item_deleted_at
    on am_archive_item (deleted_at desc, id desc)
    where deleted_flag = true;

comment on table am_archive_item is '档案条目主表';
comment on column am_archive_item.id is '主键';
comment on column am_archive_item.volume_id is '所属案卷 ID，未组卷为空';
comment on column am_archive_item.fonds_code is '全宗编码';
comment on column am_archive_item.fonds_name is '全宗名称';
comment on column am_archive_item.category_code is '档案分类编码';
comment on column am_archive_item.category_name is '档案分类名称';
comment on column am_archive_item.archive_no is '档号';
comment on column am_archive_item.electronic_status is '电子档案状态';
comment on column am_archive_item.security_level is '密级';
comment on column am_archive_item.sort_order is '排序字段';
comment on column am_archive_item.display_order is '同一案卷内卷内排序';
comment on column am_archive_item.archived_at is '归档时间';
comment on column am_archive_item.archive_year is '年度';
comment on column am_archive_item.random_bucket is '随机抽查辅助分桶';
comment on column am_archive_item.locked_flag is '业务锁定标记';
comment on column am_archive_item.lock_reason is '锁定原因';
comment on column am_archive_item.locked_by is '锁定人用户 ID';
comment on column am_archive_item.locked_at is '锁定时间';
comment on column am_archive_item.deleted_flag is '删除标记';
comment on column am_archive_item.deleted_at is '删除时间';
comment on column am_archive_item.deleted_by is '删除人用户 ID';
comment on column am_archive_item.version is '乐观锁版本号';
comment on column am_archive_item.created_by is '创建人用户 ID';
comment on column am_archive_item.created_at is '创建时间';
comment on column am_archive_item.updated_by is '更新人用户 ID';
comment on column am_archive_item.updated_at is '更新时间';

create table am_archive_item_electronic_file
(
    id                bigserial primary key,
    archive_item_id bigint      not null references am_archive_item (id),
    storage_object_id bigint      not null references am_storage_object (id),
    usage_type        varchar(50) not null default 'DEFAULT',
    display_order     integer     not null default 0,
    created_by        bigint,
    created_at        timestamp   not null default localtimestamp
);

create unique index uk_am_archive_item_electronic_file
    on am_archive_item_electronic_file (archive_item_id, storage_object_id, usage_type);
create index idx_am_archive_item_electronic_file_object
    on am_archive_item_electronic_file (storage_object_id);

comment on table am_archive_item_electronic_file is '档案条目和电子文件关联表';
comment on column am_archive_item_electronic_file.id is '主键';
comment on column am_archive_item_electronic_file.archive_item_id is '档案条目 ID，可为案卷或卷内条目';
comment on column am_archive_item_electronic_file.storage_object_id is '存储对象 ID';
comment on column am_archive_item_electronic_file.usage_type is '文件用途类型';
comment on column am_archive_item_electronic_file.display_order is '文件排序';
comment on column am_archive_item_electronic_file.created_by is '创建人用户 ID';
comment on column am_archive_item_electronic_file.created_at is '创建时间';

create table am_archive_item_relation
(
    id             bigserial primary key,
    source_item_id bigint       not null references am_archive_item (id),
    target_item_id bigint       not null references am_archive_item (id),
    created_at     timestamp    not null default localtimestamp
);

create unique index uk_am_archive_item_relation_pair
    on am_archive_item_relation (source_item_id, target_item_id);
create index idx_am_archive_item_relation_source
    on am_archive_item_relation (source_item_id, id);
create index idx_am_archive_item_relation_target
    on am_archive_item_relation (target_item_id, id)
;

comment on table am_archive_item_relation is '档案条目关联表';
comment on column am_archive_item_relation.id is '主键';
comment on column am_archive_item_relation.source_item_id is '来源条目 ID';
comment on column am_archive_item_relation.target_item_id is '目标条目 ID';
comment on column am_archive_item_relation.created_at is '创建时间';

create table am_archive_item_line_table
(
    id            bigserial primary key,
    category_id   bigint       not null references am_archive_category (id),
    table_code    varchar(80)  not null,
    table_name    varchar(255) not null,
    physical_table_name varchar(100),
    sort_order    integer      not null default 0,
    enabled       boolean      not null default true,
    deleted_flag  boolean      not null default false,
    version       integer      not null default 0,
    created_by    bigint,
    created_at    timestamp    not null default localtimestamp,
    updated_by    bigint,
    updated_at    timestamp    not null default localtimestamp
);

create unique index uk_am_archive_item_line_table_code_active
    on am_archive_item_line_table (category_id, table_code)
    where deleted_flag = false;
create index idx_am_archive_item_line_table_category_active
    on am_archive_item_line_table (category_id, sort_order, id)
    where deleted_flag = false;

comment on table am_archive_item_line_table is '档案条目明细表定义';
comment on column am_archive_item_line_table.id is '主键';
comment on column am_archive_item_line_table.category_id is '档案分类 ID';
comment on column am_archive_item_line_table.table_code is '明细表编码';
comment on column am_archive_item_line_table.table_name is '明细表名称';
comment on column am_archive_item_line_table.physical_table_name is '动态明细物理表名';
comment on column am_archive_item_line_table.sort_order is '排序字段';
comment on column am_archive_item_line_table.enabled is '是否启用';
comment on column am_archive_item_line_table.deleted_flag is '删除标记';
comment on column am_archive_item_line_table.version is '乐观锁版本号';
comment on column am_archive_item_line_table.created_by is '创建人用户 ID';
comment on column am_archive_item_line_table.created_at is '创建时间';
comment on column am_archive_item_line_table.updated_by is '更新人用户 ID';
comment on column am_archive_item_line_table.updated_at is '更新时间';

create table am_archive_item_line_field
(
    id             bigserial primary key,
    line_table_id  bigint       not null references am_archive_item_line_table (id),
    field_code     varchar(80)  not null,
    field_name     varchar(255) not null,
    field_type     varchar(30)  not null,
    column_name    varchar(80)  not null,
    exact_searchable boolean    not null default false,
    sort_order     integer      not null default 0,
    enabled        boolean      not null default true,
    deleted_flag   boolean      not null default false,
    version        integer      not null default 0,
    created_by     bigint,
    created_at     timestamp    not null default localtimestamp,
    updated_by     bigint,
    updated_at     timestamp    not null default localtimestamp
);

create unique index uk_am_archive_item_line_field_code_active
    on am_archive_item_line_field (line_table_id, field_code)
    where deleted_flag = false;
create unique index uk_am_archive_item_line_field_column_active
    on am_archive_item_line_field (line_table_id, column_name)
    where deleted_flag = false;
create index idx_am_archive_item_line_field_table_active
    on am_archive_item_line_field (line_table_id, sort_order, id)
    where deleted_flag = false;

comment on table am_archive_item_line_field is '档案条目明细字段定义';
comment on column am_archive_item_line_field.id is '主键';
comment on column am_archive_item_line_field.line_table_id is '明细表定义 ID';
comment on column am_archive_item_line_field.field_code is '字段编码';
comment on column am_archive_item_line_field.field_name is '字段名称';
comment on column am_archive_item_line_field.field_type is '字段类型';
comment on column am_archive_item_line_field.column_name is '动态列名';
comment on column am_archive_item_line_field.exact_searchable is '是否允许精确筛选';
comment on column am_archive_item_line_field.sort_order is '排序字段';
comment on column am_archive_item_line_field.enabled is '是否启用';
comment on column am_archive_item_line_field.deleted_flag is '删除标记';
comment on column am_archive_item_line_field.version is '乐观锁版本号';
comment on column am_archive_item_line_field.created_by is '创建人用户 ID';
comment on column am_archive_item_line_field.created_at is '创建时间';
comment on column am_archive_item_line_field.updated_by is '更新人用户 ID';
comment on column am_archive_item_line_field.updated_at is '更新时间';

create table am_archive_unique_constraint
(
    id            bigserial primary key,
    category_id   bigint       not null references am_archive_category (id),
    archive_level varchar(30)  not null default 'ITEM',
    constraint_code     varchar(80)  not null,
    constraint_name     varchar(255) not null,
    index_name    varchar(100) not null,
    enabled       boolean      not null default true,
    deleted_flag  boolean      not null default false,
    version       integer      not null default 0,
    created_by    bigint,
    created_at    timestamp    not null default localtimestamp,
    updated_by    bigint,
    updated_at    timestamp    not null default localtimestamp
);

create unique index uk_am_archive_unique_constraint_code_active
    on am_archive_unique_constraint (category_id, archive_level, constraint_code)
    where deleted_flag = false;
create unique index uk_am_archive_unique_constraint_index_active
    on am_archive_unique_constraint (index_name)
    where deleted_flag = false;
create index idx_am_archive_unique_constraint_category_active
    on am_archive_unique_constraint (category_id, archive_level, id)
    where deleted_flag = false;

comment on table am_archive_unique_constraint is '档案分类唯一约束表';
comment on column am_archive_unique_constraint.id is '主键';
comment on column am_archive_unique_constraint.category_id is '档案分类 ID';
comment on column am_archive_unique_constraint.archive_level is '约束适用层级：volume 案卷，item 卷内条目';
comment on column am_archive_unique_constraint.constraint_code is '约束编码';
comment on column am_archive_unique_constraint.constraint_name is '约束名称';
comment on column am_archive_unique_constraint.index_name is '动态表唯一索引名';
comment on column am_archive_unique_constraint.enabled is '是否启用';
comment on column am_archive_unique_constraint.deleted_flag is '删除标记';
comment on column am_archive_unique_constraint.version is '乐观锁版本号';
comment on column am_archive_unique_constraint.created_by is '创建人用户 ID';
comment on column am_archive_unique_constraint.created_at is '创建时间';
comment on column am_archive_unique_constraint.updated_by is '更新人用户 ID';
comment on column am_archive_unique_constraint.updated_at is '更新时间';

create table am_archive_unique_constraint_field
(
    id          bigserial primary key,
    constraint_id     bigint  not null references am_archive_unique_constraint (id),
    field_id    bigint  not null references am_archive_field (id),
    field_order integer not null default 0
);

create unique index uk_am_archive_unique_constraint_field_active
    on am_archive_unique_constraint_field (constraint_id, field_id);
create index idx_am_archive_unique_constraint_field_order
    on am_archive_unique_constraint_field (constraint_id, field_order, id);

comment on table am_archive_unique_constraint_field is '档案分类唯一约束字段表';
comment on column am_archive_unique_constraint_field.id is '主键';
comment on column am_archive_unique_constraint_field.constraint_id is '唯一约束 ID';
comment on column am_archive_unique_constraint_field.field_id is '字段定义 ID';
comment on column am_archive_unique_constraint_field.field_order is '字段顺序';

create table am_archive_field_layout
(
    id            bigserial primary key,
    category_id   bigint      not null references am_archive_category (id),
    surface       varchar(20) not null,
    field_id      bigint      not null references am_archive_field (id),
    visible       boolean     not null default true,
    list_width    integer,
    col_span      integer     not null default 1,
    row_order     integer     not null default 0,
    col_order     integer     not null default 0,
    deleted_flag  boolean     not null default false,
    version       integer     not null default 0,
    created_by    bigint,
    created_at    timestamp   not null default localtimestamp,
    updated_by    bigint,
    updated_at    timestamp   not null default localtimestamp
);

create unique index uk_am_archive_field_layout_public_active
    on am_archive_field_layout (category_id, surface, field_id)
    where deleted_flag = false;
create index idx_am_archive_field_layout_order_active
    on am_archive_field_layout (category_id, surface, row_order, col_order, id)
    where deleted_flag = false;

comment on table am_archive_field_layout is '档案分类字段布局配置表';
comment on column am_archive_field_layout.id is '主键';
comment on column am_archive_field_layout.category_id is '档案分类 ID';
comment on column am_archive_field_layout.surface is '布局场景：table 表格，detail 详情，edit 编辑';
comment on column am_archive_field_layout.field_id is '字段定义 ID';
comment on column am_archive_field_layout.visible is '该布局是否显示字段';
comment on column am_archive_field_layout.list_width is '表格列宽';
comment on column am_archive_field_layout.col_span is '详情或编辑布局跨列数';
comment on column am_archive_field_layout.row_order is '布局行顺序';
comment on column am_archive_field_layout.col_order is '布局列顺序';
comment on column am_archive_field_layout.deleted_flag is '删除标记';
comment on column am_archive_field_layout.version is '乐观锁版本号';
comment on column am_archive_field_layout.created_by is '创建人用户 ID';
comment on column am_archive_field_layout.created_at is '创建时间';
comment on column am_archive_field_layout.updated_by is '更新人用户 ID';
comment on column am_archive_field_layout.updated_at is '更新时间';

create table am_archive_item_search
(
    id                bigserial primary key,
    archive_item_id bigint       not null references am_archive_item (id),
    search_text       text         not null,
    index_version     integer      not null default 1,
    created_at        timestamp    not null default localtimestamp,
    updated_at        timestamp    not null default localtimestamp
);

create unique index uk_am_archive_item_search_item
    on am_archive_item_search (archive_item_id);
create index idx_am_archive_item_search_trgm
    on am_archive_item_search using gin (search_text gin_trgm_ops);

comment on table am_archive_item_search is '档案条目全文检索投影表';
comment on column am_archive_item_search.id is '主键';
comment on column am_archive_item_search.archive_item_id is '档案条目 ID';
comment on column am_archive_item_search.search_text is '全文检索拼接文本';
comment on column am_archive_item_search.index_version is '索引版本';
comment on column am_archive_item_search.created_at is '创建时间';
comment on column am_archive_item_search.updated_at is '更新时间';

create table am_archive_item_search_outbox
(
    id                bigserial primary key,
    archive_item_id bigint      not null,
    event_type        varchar(20) not null,
    status            varchar(20) not null default 'PENDING',
    attempts          integer     not null default 0,
    last_error        text,
    next_retry_at     timestamp,
    created_at        timestamp   not null default localtimestamp,
    updated_at        timestamp   not null default localtimestamp,
    processed_at      timestamp
);

create index idx_am_archive_item_search_outbox_pending
    on am_archive_item_search_outbox (status, next_retry_at, id);
create index idx_am_archive_item_search_outbox_item
    on am_archive_item_search_outbox (archive_item_id, id);

comment on table am_archive_item_search_outbox is '档案条目全文检索投影 outbox';
comment on column am_archive_item_search_outbox.id is '主键';
comment on column am_archive_item_search_outbox.archive_item_id is '档案条目 ID';
comment on column am_archive_item_search_outbox.event_type is '事件类型';
comment on column am_archive_item_search_outbox.status is '处理状态';
comment on column am_archive_item_search_outbox.attempts is '处理次数';
comment on column am_archive_item_search_outbox.last_error is '最近错误';
comment on column am_archive_item_search_outbox.next_retry_at is '下次重试时间';
comment on column am_archive_item_search_outbox.created_at is '创建时间';
comment on column am_archive_item_search_outbox.updated_at is '更新时间';
comment on column am_archive_item_search_outbox.processed_at is '处理时间';

create table am_archive_item_audit
(
    id                bigserial primary key,
    source_table_name varchar(100) not null,
    source_item_id  bigint      not null,
    archive_item_id bigint,
    fonds_code        varchar(100),
    category_code     varchar(100),
    operation_type    varchar(50)  not null,
    operation_reason  varchar(500),
    operated_by       bigint,
    operated_at       timestamp   not null default localtimestamp
);

create index idx_am_archive_item_audit_item on am_archive_item_audit (archive_item_id);
create index idx_am_archive_item_audit_category on am_archive_item_audit (fonds_code, category_code);
create index idx_am_archive_item_audit_operation on am_archive_item_audit (operation_type, operated_at);

comment on table am_archive_item_audit is '档案条目操作审计表';
comment on column am_archive_item_audit.id is '主键';
comment on column am_archive_item_audit.source_table_name is '来源表名';
comment on column am_archive_item_audit.source_item_id is '来源条目 ID';
comment on column am_archive_item_audit.archive_item_id is '档案条目主表 ID';
comment on column am_archive_item_audit.fonds_code is '全宗编码';
comment on column am_archive_item_audit.category_code is '档案分类编码';
comment on column am_archive_item_audit.operation_type is '操作类型';
comment on column am_archive_item_audit.operation_reason is '操作原因';
comment on column am_archive_item_audit.operated_by is '操作人用户 ID';
comment on column am_archive_item_audit.operated_at is '操作时间';
