create table am_organization_department
(
    id              bigserial primary key,
    department_code varchar(100) not null,
    department_name varchar(255) not null,
    parent_id       bigint references am_organization_department (id),
    enabled         boolean      not null default true,
    sort_order      integer      not null default 0,
    version         integer      not null default 0,
    created_at      timestamp    not null default localtimestamp,
    updated_at      timestamp    not null default localtimestamp
);

create unique index uk_am_organization_department_code on am_organization_department (department_code);
create index idx_am_organization_department_parent
    on am_organization_department (parent_id, sort_order, id);

comment on table am_organization_department is '组织架构部门表';
comment on column am_organization_department.department_code is '部门编码';
comment on column am_organization_department.department_name is '部门名称';
comment on column am_organization_department.parent_id is '父部门 ID';
comment on column am_organization_department.enabled is '是否启用';

alter table am_archive_volume
    rename column org_unit_id to department_id;

alter table am_archive_item
    rename column org_unit_id to department_id;

comment on column am_archive_volume.department_id is '所属部门 ID';
comment on column am_archive_item.department_id is '所属部门 ID';

alter table am_archive_volume
    add constraint fk_am_archive_volume_department
        foreign key (department_id) references am_organization_department (id);

alter table am_archive_item
    add constraint fk_am_archive_item_department
        foreign key (department_id) references am_organization_department (id);
