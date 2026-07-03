create table am_organization_unit
(
    id          bigserial primary key,
    unit_code   varchar(100) not null,
    unit_name   varchar(255) not null,
    parent_id   bigint references am_organization_unit (id),
    enabled     boolean      not null default true,
    sort_order  integer      not null default 0,
    version     integer      not null default 0,
    created_at  timestamp    not null default localtimestamp,
    updated_at  timestamp    not null default localtimestamp
);

create unique index uk_am_organization_unit_code on am_organization_unit (unit_code);
create index idx_am_organization_unit_parent on am_organization_unit (parent_id, sort_order, id);

comment on table am_organization_unit is '组织单元字典表';
comment on column am_organization_unit.unit_code is '组织单元编码';
comment on column am_organization_unit.unit_name is '组织单元名称';
comment on column am_organization_unit.parent_id is '父级组织单元 ID';
comment on column am_organization_unit.enabled is '是否启用';

alter table am_archive_volume
    add constraint fk_am_archive_volume_org_unit
        foreign key (org_unit_id) references am_organization_unit (id);

alter table am_archive_item
    add constraint fk_am_archive_item_org_unit
        foreign key (org_unit_id) references am_organization_unit (id);
