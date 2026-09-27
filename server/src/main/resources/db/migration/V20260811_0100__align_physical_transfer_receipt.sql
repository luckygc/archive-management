alter table am_archive_physical_object
    add column custody_status varchar(30) not null default 'ARCHIVE_ROOM_CUSTODY';

alter table am_archive_physical_object
    add constraint ck_am_archive_physical_object_custody_status
        check (custody_status in
            ('DEPARTMENT_CUSTODY', 'PENDING_RECEIPT', 'ARCHIVE_ROOM_CUSTODY'));

comment on column am_archive_physical_object.custody_status is
    '实物保管状态：DEPARTMENT_CUSTODY 业务部门保管，PENDING_RECEIPT 待档案室接收，ARCHIVE_ROOM_CUSTODY 档案室保管';

with system_holding as
(
    select min(id) as id
    from am_archive_repository
    where repository_role = 'HOLDING'
      and system_flag = true
      and deleted_flag = false
)
update am_archive_item item
set repository_id = system_holding.id,
    updated_at = localtimestamp
from system_holding
where item.repository_id in
    (
        select id
        from am_archive_repository
        where repository_role = 'TRANSFER'
          and deleted_flag = false
    );

with system_holding as
(
    select min(id) as id
    from am_archive_repository
    where repository_role = 'HOLDING'
      and system_flag = true
      and deleted_flag = false
)
update am_archive_volume volume
set repository_id = system_holding.id,
    updated_at = localtimestamp
from system_holding
where volume.repository_id in
    (
        select id
        from am_archive_repository
        where repository_role = 'TRANSFER'
          and deleted_flag = false
    );

update am_archive_repository
set enabled = false,
    deleted_flag = true,
    updated_at = localtimestamp
where repository_role = 'TRANSFER'
  and deleted_flag = false;

alter table am_archive_repository
    add constraint ck_am_archive_repository_role
        check (deleted_flag or repository_role in ('INTAKE', 'HOLDING'));

comment on column am_archive_repository.repository_role is
    '业务库角色：INTAKE 预归档，HOLDING 室藏';

create table am_archive_physical_transfer
(
    id                   bigserial primary key,
    transfer_no          varchar(80)  not null,
    source_department_id bigint       not null references am_organization_department (id),
    status               varchar(30)  not null,
    remark               varchar(1000),
    submitted_by         bigint       not null,
    submitted_at         timestamp    not null,
    received_by          bigint,
    received_at          timestamp,
    receipt_note         varchar(1000),
    rejected_by          bigint,
    rejected_at          timestamp,
    rejection_reason     varchar(1000),
    version              integer      not null default 0,
    created_by           bigint,
    created_at           timestamp    not null default localtimestamp,
    updated_by           bigint,
    updated_at           timestamp    not null default localtimestamp,
    constraint ck_am_archive_physical_transfer_status
        check (status in ('PENDING_RECEIPT', 'ACCEPTED', 'REJECTED')),
    constraint ck_am_archive_physical_transfer_completion
        check (
            (status = 'PENDING_RECEIPT'
                and received_by is null
                and received_at is null
                and rejected_by is null
                and rejected_at is null
                and rejection_reason is null)
            or
            (status = 'ACCEPTED'
                and received_by is not null
                and received_at is not null
                and rejected_by is null
                and rejected_at is null
                and rejection_reason is null)
            or
            (status = 'REJECTED'
                and received_by is null
                and received_at is null
                and receipt_note is null
                and rejected_by is not null
                and rejected_at is not null
                and rejection_reason is not null)
        )
);

create unique index uk_am_archive_physical_transfer_no
    on am_archive_physical_transfer (transfer_no);
create index idx_am_archive_physical_transfer_department_status
    on am_archive_physical_transfer (source_department_id, status, submitted_at desc, id desc);

comment on table am_archive_physical_transfer is '业务部门向档案室提交的实物移交批次';
comment on column am_archive_physical_transfer.transfer_no is '唯一移交编号';
comment on column am_archive_physical_transfer.source_department_id is '移交来源部门 ID';
comment on column am_archive_physical_transfer.status is '批次状态：PENDING_RECEIPT 待接收，ACCEPTED 已接收，REJECTED 已退回';

create table am_archive_physical_transfer_item
(
    id                      bigserial primary key,
    transfer_id             bigint       not null references am_archive_physical_transfer (id),
    physical_object_id      bigint       not null references am_archive_physical_object (id),
    archive_item_id         bigint references am_archive_item (id),
    archive_volume_id       bigint references am_archive_volume (id),
    barcode_snapshot        varchar(120),
    carrier_type_snapshot   varchar(80),
    quantity_snapshot       numeric(18, 4),
    quantity_unit_snapshot  varchar(30),
    condition_note_snapshot varchar(255),
    active_flag             boolean      not null default true,
    version                 integer      not null default 0,
    created_by              bigint,
    created_at              timestamp    not null default localtimestamp,
    updated_by              bigint,
    updated_at              timestamp    not null default localtimestamp,
    constraint ck_am_archive_physical_transfer_item_owner
        check ((archive_item_id is not null and archive_volume_id is null)
            or (archive_item_id is null and archive_volume_id is not null)),
    constraint uk_am_archive_physical_transfer_item
        unique (transfer_id, physical_object_id)
);

create unique index uk_am_archive_physical_transfer_item_active
    on am_archive_physical_transfer_item (physical_object_id)
    where active_flag = true;
create index idx_am_archive_physical_transfer_item_transfer
    on am_archive_physical_transfer_item (transfer_id, id);

comment on table am_archive_physical_transfer_item is '实物移交批次清单快照';
comment on column am_archive_physical_transfer_item.physical_object_id is '移交实物对象 ID';
comment on column am_archive_physical_transfer_item.archive_item_id is '提交时所属档案条目 ID，与案卷 ID 二选一';
comment on column am_archive_physical_transfer_item.archive_volume_id is '提交时所属案卷 ID，与条目 ID 二选一';
comment on column am_archive_physical_transfer_item.active_flag is '是否仍占用实物的活动移交约束';
