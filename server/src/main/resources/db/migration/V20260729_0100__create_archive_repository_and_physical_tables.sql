create table am_archive_repository
(
    id              bigserial primary key,
    repository_code varchar(80)  not null,
    repository_name varchar(255) not null,
    repository_role varchar(30)  not null,
    enabled         boolean      not null default true,
    sort_order      integer      not null default 0,
    system_flag     boolean      not null default false,
    deleted_flag    boolean      not null default false,
    version         integer      not null default 0,
    created_by      bigint,
    created_at      timestamp    not null default localtimestamp,
    updated_by      bigint,
    updated_at      timestamp    not null default localtimestamp
);

create unique index uk_am_archive_repository_code_active
    on am_archive_repository (repository_code)
    where deleted_flag = false;
create index idx_am_archive_repository_role_active
    on am_archive_repository (repository_role, sort_order, id)
    where deleted_flag = false;

comment on table am_archive_repository is '档案业务库';
comment on column am_archive_repository.repository_code is '业务库编码';
comment on column am_archive_repository.repository_name is '业务库名称';
comment on column am_archive_repository.repository_role is '业务库角色：INTAKE 预归档，HOLDING 室藏，TRANSFER 移交';
comment on column am_archive_repository.system_flag is '是否系统内置业务库';

insert into am_archive_repository
    (id, repository_code, repository_name, repository_role, enabled, sort_order, system_flag)
values
    (1, 'SYSTEM_INTAKE', '预归档库', 'INTAKE', true, 10, true),
    (2, 'SYSTEM_HOLDING', '室藏库', 'HOLDING', true, 20, true),
    (3, 'SYSTEM_TRANSFER', '移交库', 'TRANSFER', true, 30, true);

select setval(
    pg_get_serial_sequence('am_archive_repository', 'id'),
    (select max(id) from am_archive_repository));

alter table am_archive_item
    add column repository_id bigint not null default 2
        references am_archive_repository (id);
alter table am_archive_volume
    add column repository_id bigint not null default 2
        references am_archive_repository (id);

create index idx_am_archive_item_repository_active
    on am_archive_item (repository_id, created_at desc, id desc)
    where deleted_flag = false;
create index idx_am_archive_volume_repository_active
    on am_archive_volume (repository_id, created_at desc, id desc)
    where deleted_flag = false;

comment on column am_archive_item.repository_id is '档案业务库 ID';
comment on column am_archive_volume.repository_id is '档案业务库 ID';

create table am_archive_repository_change_history
(
    id                 bigserial primary key,
    archive_type       varchar(20) not null,
    archive_id         bigint      not null,
    from_repository_id bigint references am_archive_repository (id),
    to_repository_id   bigint      not null references am_archive_repository (id),
    business_type      varchar(80),
    business_id        bigint,
    reason             varchar(500),
    operated_by        bigint,
    operated_at        timestamp   not null default localtimestamp,
    created_by         bigint,
    created_at         timestamp   not null default localtimestamp
);

create index idx_am_archive_repository_change_archive
    on am_archive_repository_change_history (archive_type, archive_id, operated_at desc, id desc);

comment on table am_archive_repository_change_history is '档案业务库变更历史';
comment on column am_archive_repository_change_history.archive_type is '档案对象类型：ITEM 条目，VOLUME 案卷';
comment on column am_archive_repository_change_history.archive_id is '档案对象 ID';
comment on column am_archive_repository_change_history.business_type is '来源业务类型';
comment on column am_archive_repository_change_history.business_id is '来源业务 ID';

create table am_archive_warehouse
(
    id             bigserial primary key,
    warehouse_code varchar(80)  not null,
    warehouse_name varchar(255) not null,
    enabled        boolean      not null default true,
    sort_order     integer      not null default 0,
    deleted_flag   boolean      not null default false,
    version        integer      not null default 0,
    created_by     bigint,
    created_at     timestamp    not null default localtimestamp,
    updated_by     bigint,
    updated_at     timestamp    not null default localtimestamp
);

create unique index uk_am_archive_warehouse_code_active
    on am_archive_warehouse (warehouse_code)
    where deleted_flag = false;
create index idx_am_archive_warehouse_active
    on am_archive_warehouse (sort_order, id)
    where deleted_flag = false;

comment on table am_archive_warehouse is '档案实物真实库房';
comment on column am_archive_warehouse.warehouse_code is '库房编码';
comment on column am_archive_warehouse.warehouse_name is '库房名称';

create table am_archive_storage_location
(
    id              bigserial primary key,
    warehouse_id    bigint       not null references am_archive_warehouse (id),
    parent_id       bigint references am_archive_storage_location (id),
    location_code   varchar(80)  not null,
    location_name   varchar(255) not null,
    location_type   varchar(50)  not null,
    enabled         boolean      not null default true,
    sort_order      integer      not null default 0,
    deleted_flag    boolean      not null default false,
    version         integer      not null default 0,
    created_by      bigint,
    created_at      timestamp    not null default localtimestamp,
    updated_by      bigint,
    updated_at      timestamp    not null default localtimestamp
);

create unique index uk_am_archive_storage_location_code_active
    on am_archive_storage_location (warehouse_id, location_code)
    where deleted_flag = false;
create index idx_am_archive_storage_location_parent_active
    on am_archive_storage_location (warehouse_id, parent_id, sort_order, id)
    where deleted_flag = false;

comment on table am_archive_storage_location is '档案实物存放位置';
comment on column am_archive_storage_location.warehouse_id is '所属真实库房 ID';
comment on column am_archive_storage_location.parent_id is '父位置 ID';
comment on column am_archive_storage_location.location_type is '位置类型，例如 ROOM、AREA、RACK、SHELF';

create table am_archive_physical_object
(
    id                  bigserial primary key,
    archive_item_id     bigint references am_archive_item (id),
    archive_volume_id   bigint references am_archive_volume (id),
    barcode             varchar(120),
    carrier_type        varchar(80),
    quantity            numeric(18, 4),
    quantity_unit       varchar(30),
    condition_note      varchar(255),
    current_location_id bigint references am_archive_storage_location (id),
    remark              varchar(1000),
    deleted_flag        boolean   not null default false,
    version             integer   not null default 0,
    created_by          bigint,
    created_at          timestamp not null default localtimestamp,
    updated_by          bigint,
    updated_at          timestamp not null default localtimestamp,
    constraint ck_am_archive_physical_object_owner
        check ((archive_item_id is not null and archive_volume_id is null)
            or (archive_item_id is null and archive_volume_id is not null))
);

create unique index uk_am_archive_physical_object_item_active
    on am_archive_physical_object (archive_item_id)
    where archive_item_id is not null and deleted_flag = false;
create unique index uk_am_archive_physical_object_volume_active
    on am_archive_physical_object (archive_volume_id)
    where archive_volume_id is not null and deleted_flag = false;
create index idx_am_archive_physical_object_location_active
    on am_archive_physical_object (current_location_id, id)
    where deleted_flag = false;

comment on table am_archive_physical_object is '档案实物对象';
comment on column am_archive_physical_object.archive_item_id is '所属档案条目 ID，与案卷 ID 二选一';
comment on column am_archive_physical_object.archive_volume_id is '所属案卷 ID，与条目 ID 二选一';
comment on column am_archive_physical_object.barcode is '实物条码';
comment on column am_archive_physical_object.carrier_type is '载体类型';
comment on column am_archive_physical_object.quantity is '实物数量';
comment on column am_archive_physical_object.quantity_unit is '数量单位';
comment on column am_archive_physical_object.condition_note is '完好状况';
comment on column am_archive_physical_object.current_location_id is '当前存放位置 ID';

create table am_archive_physical_location_history
(
    id                 bigserial primary key,
    physical_object_id bigint      not null references am_archive_physical_object (id),
    from_location_id   bigint references am_archive_storage_location (id),
    to_location_id     bigint      not null references am_archive_storage_location (id),
    business_type      varchar(80),
    business_id        bigint,
    reason             varchar(500),
    operated_by        bigint,
    operated_at        timestamp   not null default localtimestamp,
    created_by         bigint,
    created_at         timestamp   not null default localtimestamp
);

create index idx_am_archive_physical_location_history_object
    on am_archive_physical_location_history
        (physical_object_id, operated_at desc, id desc);

comment on table am_archive_physical_location_history is '档案实物位置变更历史';
comment on column am_archive_physical_location_history.business_type is '来源业务类型';
comment on column am_archive_physical_location_history.business_id is '来源业务 ID';
