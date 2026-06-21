create table am_archive_record
(
    id                  bigserial primary key,
    fonds_code          varchar(100) not null,
    category_group_code varchar(100),
    category_code       varchar(100) not null,
    category_name       varchar(255),
    archive_status      varchar(50)  not null,
    process_status     varchar(50)  not null,
    security_level      varchar(50),
    organization_id     bigint,
    organization_code   varchar(100),
    sort_order          integer      not null default 0,
    archived_at         timestamp,
    deleted_flag        boolean      not null default false,
    archive_year        integer      not null,
    created_by          bigint,
    created_at          timestamp    not null default localtimestamp,
    updated_by          bigint,
    updated_at          timestamp    not null default localtimestamp
);

create index idx_am_archive_record_category_active
    on am_archive_record (fonds_code, category_code)
    where deleted_flag = false;
create index idx_am_archive_record_year_active
    on am_archive_record (archive_year)
    where deleted_flag = false;
create index idx_am_archive_record_organization_active
    on am_archive_record (organization_id, organization_code)
    where deleted_flag = false;
create index idx_am_archive_record_sort_active
    on am_archive_record (sort_order, id)
    where deleted_flag = false;
create index idx_am_archive_record_created_at on am_archive_record (created_at);

comment on table am_archive_record is '档案记录表';
comment on column am_archive_record.id is '主键';
comment on column am_archive_record.fonds_code is '全宗编码';
comment on column am_archive_record.category_group_code is '档案分类分组编码,门类编码';
comment on column am_archive_record.category_code is '档案分类编码';
comment on column am_archive_record.category_name is '档案分类名称';
comment on column am_archive_record.archive_status is '档案状态';
comment on column am_archive_record.process_status is '流程状态';
comment on column am_archive_record.security_level is '密级';
comment on column am_archive_record.organization_id is '机构 ID';
comment on column am_archive_record.organization_code is '机构编码';
comment on column am_archive_record.sort_order is '排序字段';
comment on column am_archive_record.archived_at is '归档时间';
comment on column am_archive_record.deleted_flag is '删除标记';
comment on column am_archive_record.archive_year is '年度';
comment on column am_archive_record.created_by is '创建人用户 ID';
comment on column am_archive_record.created_at is '创建时间';
comment on column am_archive_record.updated_by is '更新人用户 ID';
comment on column am_archive_record.updated_at is '更新时间';

create table am_archive_volume
(
    id            bigserial primary key,
    fonds_code    varchar(100) not null,
    category_code varchar(100) not null,
    volume_no     varchar(100) not null,
    sort_order    integer      not null default 0,
    deleted_flag  boolean      not null default false,
    created_by    bigint,
    created_at    timestamp    not null default localtimestamp,
    updated_by    bigint,
    updated_at    timestamp    not null default localtimestamp
);

create unique index uk_am_archive_volume_no_active
    on am_archive_volume (fonds_code, category_code, volume_no)
    where deleted_flag = false;
create index idx_am_archive_volume_sort_active
    on am_archive_volume (sort_order, id)
    where deleted_flag = false;
create index idx_am_archive_volume_created_at on am_archive_volume (created_at);

comment on table am_archive_volume is '档案案卷表';
comment on column am_archive_volume.id is '主键';
comment on column am_archive_volume.fonds_code is '全宗编码';
comment on column am_archive_volume.category_code is '档案分类编码';
comment on column am_archive_volume.volume_no is '案卷号';
comment on column am_archive_volume.sort_order is '排序字段';
comment on column am_archive_volume.deleted_flag is '删除标记';
comment on column am_archive_volume.created_by is '创建人用户 ID';
comment on column am_archive_volume.created_at is '创建时间';
comment on column am_archive_volume.updated_by is '更新人用户 ID';
comment on column am_archive_volume.updated_at is '更新时间';

create table am_archive_volume_item
(
    id                bigserial primary key,
    volume_id         bigint    not null references am_archive_volume (id),
    archive_record_id bigint    not null references am_archive_record (id),
    display_order     integer   not null default 0,
    created_by        bigint,
    created_at        timestamp not null default localtimestamp,
    deleted_flag      boolean   not null default false
);

create unique index uk_am_archive_volume_item_active
    on am_archive_volume_item (volume_id, archive_record_id)
    where deleted_flag = false;
create index idx_am_archive_volume_item_record_active
    on am_archive_volume_item (archive_record_id)
    where deleted_flag = false;

comment on table am_archive_volume_item is '案卷卷内档案记录关联表';
comment on column am_archive_volume_item.id is '主键';
comment on column am_archive_volume_item.volume_id is '案卷 ID';
comment on column am_archive_volume_item.archive_record_id is '档案记录 ID';
comment on column am_archive_volume_item.display_order is '卷内排序';
comment on column am_archive_volume_item.created_by is '创建人用户 ID';
comment on column am_archive_volume_item.created_at is '创建时间';
comment on column am_archive_volume_item.deleted_flag is '删除标记';

create table am_archive_record_storage_object
(
    id                bigserial primary key,
    archive_record_id bigint      not null references am_archive_record (id),
    storage_object_id bigint      not null references am_storage_object (id),
    usage_type        varchar(50) not null default 'DEFAULT',
    display_order     integer     not null default 0,
    created_by        bigint,
    created_at        timestamp   not null default localtimestamp,
    deleted_flag      boolean     not null default false
);

create unique index uk_am_archive_record_storage_object_active
    on am_archive_record_storage_object (archive_record_id, storage_object_id, usage_type)
    where deleted_flag = false;
create index idx_am_archive_record_storage_object_file_active
    on am_archive_record_storage_object (storage_object_id)
    where deleted_flag = false;

comment on table am_archive_record_storage_object is '档案记录和存储对象关联表';
comment on column am_archive_record_storage_object.id is '主键';
comment on column am_archive_record_storage_object.archive_record_id is '档案记录 ID';
comment on column am_archive_record_storage_object.storage_object_id is '存储对象 ID';
comment on column am_archive_record_storage_object.usage_type is '文件用途类型';
comment on column am_archive_record_storage_object.display_order is '文件排序';
comment on column am_archive_record_storage_object.created_by is '创建人用户 ID';
comment on column am_archive_record_storage_object.created_at is '创建时间';
comment on column am_archive_record_storage_object.deleted_flag is '删除标记';
